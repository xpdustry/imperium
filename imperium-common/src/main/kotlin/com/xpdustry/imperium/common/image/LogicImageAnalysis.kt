/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.common.image

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.database.mongo.MongoEntityCollection
import com.xpdustry.imperium.common.database.mongo.MongoProvider
import com.xpdustry.imperium.common.geometry.Cluster
import com.xpdustry.imperium.common.hash.ShaHashFunction
import com.xpdustry.imperium.common.hash.ShaType
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.toBase64
import com.xpdustry.imperium.common.storage.StorageBucket
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.BsonDocument
import org.bson.Document
import org.bson.types.ObjectId
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.Arrays

interface LogicImageAnalysis {
    suspend fun isUnsafe(blocks: List<Cluster.Block<out LogicImage>>): Pair<Boolean, ObjectId?>
    suspend fun findHashedImageById(id: ObjectId): Pair<HashedLogicImage, StorageBucket.S3Object>?
    suspend fun updateSafetyById(id: ObjectId, unsafe: Boolean)
}

internal class SimpleLogicImageAnalysis(
    private val mongo: MongoProvider,
    private val storage: StorageBucket,
    private val detection: ImageAnalysis,
) : LogicImageAnalysis, ImperiumApplication.Listener {

    private lateinit var collection: MongoEntityCollection<HashedLogicImage, ObjectId>

    override fun onImperiumInit() {
        collection = mongo.getCollection("hashed-logic-images", HashedLogicImage::class)
    }

    override suspend fun isUnsafe(blocks: List<Cluster.Block<out LogicImage>>): Pair<Boolean, ObjectId?> = withContext(ImperiumScope.IO.coroutineContext) {
        val (hashes, entry) = findHashedImageByImages(blocks)
        if (entry != null) {
            return@withContext entry.unsafe to entry._id
        }
        val image = createImage(blocks)
        when (val result = detection.isUnsafe(image)) {
            is ImageAnalysis.Result.Success -> {
                // TODO Embed details in hash
                if (result.details.values.any { it > 0F }) {
                    val hashed = HashedLogicImage(unsafe = result.unsafe, hashes = hashes)
                    launch {
                        collection.save(hashed)
                        storage.getObject("images", "unsafe", "${hashed._id.toHexString()}.jpg").putData(image.inputStream(ImageFormat.JPG))
                    }
                    result.unsafe to hashed._id
                } else {
                    result.unsafe to null
                }
            }
            is ImageAnalysis.Result.Failure -> {
                logger.error("Failed to analyze image: {}", result.message)
                false to null
            }
        }
    }

    override suspend fun findHashedImageById(id: ObjectId): Pair<HashedLogicImage, StorageBucket.S3Object>? =
        collection.findById(id)?.let { it to storage.getObject("images", "unsafe", "${it._id.toHexString()}.jpg") }

    override suspend fun updateSafetyById(id: ObjectId, unsafe: Boolean) {
        collection.findById(id)?.let {
            it.unsafe = unsafe
            collection.save(it)
        }
    }

    private suspend fun hash(blocks: List<Cluster.Block<out LogicImage>>): Set<String> = withContext(ImperiumScope.IO.coroutineContext) {
        val result = mutableSetOf<String>()
        for (block in blocks) {
            when (val image = block.data) {
                is LogicImage.PixMap -> {
                    val integers = image.pixels.entries.sortedBy(Map.Entry<Int, Int>::key).map(Map.Entry<Int, Int>::value)
                    val buffer = ByteBuffer.allocate(integers.size * 4)
                    for (integer in integers) buffer.putInt(integer)
                    result += ShaHashFunction.create(buffer.array(), ShaType.SHA256).hash.toBase64()
                }
                is LogicImage.Drawer -> {
                    for (processor in image.processors) {
                        val stream = ByteArrayOutputStream()
                        val output = DataOutputStream(stream)
                        for (instruction in processor.instructions.sortedWith { a, b -> Arrays.compare(toIntArray(a), toIntArray(b)) }) {
                            when (instruction) {
                                is LogicImage.Drawer.Instruction.Color -> {
                                    output.writeByte(instruction.r)
                                    output.writeByte(instruction.g)
                                    output.writeByte(instruction.b)
                                    output.writeByte(instruction.a)
                                }
                                is LogicImage.Drawer.Instruction.Rect -> {
                                    output.writeShort(instruction.x)
                                    output.writeShort(instruction.y)
                                    output.writeShort(instruction.w)
                                    output.writeShort(instruction.h)
                                }
                                is LogicImage.Drawer.Instruction.Triangle -> {
                                    output.writeShort(instruction.x1)
                                    output.writeShort(instruction.y1)
                                    output.writeShort(instruction.x2)
                                    output.writeShort(instruction.y2)
                                    output.writeShort(instruction.x3)
                                    output.writeShort(instruction.y3)
                                }
                            }
                        }
                        result += ShaHashFunction.create(stream.toByteArray(), ShaType.SHA256).hash.toBase64()
                    }
                }
            }
        }
        return@withContext result
    }

    // TODO Improve pipeline
    private suspend fun findHashedImageByImages(blocks: List<Cluster.Block<out LogicImage>>): Pair<Set<String>, HashedLogicImage?> {
        val hashes = hash(blocks)
        // This pipeline is kindly provided to you by ChatGPT
        val result = collection.aggregate(
            Document(
                "\$match",
                Document(
                    "hashes",
                    Document("\$in", hashes),
                ),
            ),
            Document(
                "\$addFields",
                Document(
                    "matchedCount",
                    Document(
                        "\$size",
                        Document("\$setIntersection", listOf("\$hashes", hashes)),
                    ),
                ),
            ),
            Document(
                "\$addFields",
                Document(
                    "totalCount",
                    Document("\$size", "\$hashes"),
                ),
            ),
            Document(
                "\$addFields",
                Document(
                    "matchPercentage",
                    Document(
                        "\$multiply",
                        listOf(Document("\$divide", mutableListOf("\$matchedCount", "\$totalCount")), 100L),
                    ),
                ),
            ),
            Document(
                "\$sort",
                Document("matchPercentage", -1L),
            ),
            Document("\$limit", 1L),
            Document(
                "\$project",
                Document("_id", 1L).append("matchedCount", 1L).append("matchPercentage", 1L),
            ),
            result = BsonDocument::class,
        ).firstOrNull()

        return if (result != null && (result.getDouble("matchPercentage").value >= 80 || result.getInt32("matchedCount").value == hashes.size)) {
            hashes to collection.findById(result.getObjectId("_id").value)!!
        } else {
            hashes to null
        }
    }

    private fun toIntArray(instruction: LogicImage.Drawer.Instruction) = when (instruction) {
        is LogicImage.Drawer.Instruction.Color -> intArrayOf(0, instruction.r, instruction.g, instruction.b, instruction.a)
        is LogicImage.Drawer.Instruction.Rect -> intArrayOf(1, instruction.x, instruction.y, instruction.w, instruction.h)
        is LogicImage.Drawer.Instruction.Triangle -> intArrayOf(2, instruction.x1, instruction.y1, instruction.x2, instruction.y2, instruction.x3, instruction.y3)
    }

    private fun createImage(blocks: List<Cluster.Block<out LogicImage>>): BufferedImage {
        val x = blocks.minOf { it.x }
        val y = blocks.minOf { it.y }
        val w = blocks.maxOf { it.x + it.size } - x
        val h = blocks.maxOf { it.y + it.size } - y

        val image = BufferedImage(w * RES_PER_BLOCK, h * RES_PER_BLOCK, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.graphics
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, image.width, image.height)

        for (block in blocks) {
            graphics.drawImage(
                createImage(block.data),
                (block.x - x) * RES_PER_BLOCK,
                (block.y - y) * RES_PER_BLOCK,
                block.size * RES_PER_BLOCK,
                block.size * RES_PER_BLOCK,
                null,
            )
        }

        // Invert y-axis, because mindustry uses bottom-left as origin
        val inverted = invertYAxis(image)
        graphics.dispose()
        return inverted
    }

    private fun createImage(image: LogicImage): BufferedImage {
        var output = BufferedImage(image.resolution, image.resolution, BufferedImage.TYPE_INT_RGB)
        val graphics = output.graphics
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, output.width, output.height)

        when (image) {
            is LogicImage.PixMap -> {
                for (pixel in image.pixels) {
                    output.setRGB(pixel.key % image.resolution, pixel.key / image.resolution, pixel.value)
                }
                output = invertYAxis(output)
            }

            is LogicImage.Drawer -> {
                for (processor in image.processors) {
                    for (instruction in processor.instructions) {
                        when (instruction) {
                            is LogicImage.Drawer.Instruction.Color -> {
                                graphics.color = Color(instruction.r, instruction.g, instruction.b, instruction.a)
                            }

                            is LogicImage.Drawer.Instruction.Rect -> {
                                if (instruction.w == 1 && instruction.h == 1) {
                                    output.setRGB(instruction.x, instruction.y, graphics.color.rgb)
                                } else {
                                    graphics.fillRect(instruction.x, instruction.y, instruction.w, instruction.h)
                                }
                            }

                            is LogicImage.Drawer.Instruction.Triangle -> {
                                graphics.fillPolygon(
                                    intArrayOf(instruction.x1, instruction.x2, instruction.x3),
                                    intArrayOf(instruction.y1, instruction.y2, instruction.y3),
                                    3,
                                )
                            }
                        }
                    }
                }
            }
        }

        graphics.dispose()
        return output
    }

    private fun invertYAxis(image: BufferedImage): BufferedImage {
        val transform = AffineTransform.getScaleInstance(1.0, -1.0)
        transform.translate(0.0, -image.getHeight(null).toDouble())
        val op = AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR)
        return op.filter(image, null)
    }

    companion object {
        private const val RES_PER_BLOCK = 30
        private val logger by LoggerDelegate()
    }
}
