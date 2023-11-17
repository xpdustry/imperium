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
import com.xpdustry.imperium.common.geometry.Cluster
import com.xpdustry.imperium.common.misc.LoggerDelegate
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import kotlinx.coroutines.withContext

interface LogicImageAnalysis {
    suspend fun isUnsafe(blocks: List<Cluster.Block<out LogicImage>>): Boolean
}

internal class SimpleLogicImageAnalysis(
    private val analysis: ImageAnalysis,
) : LogicImageAnalysis, ImperiumApplication.Listener {

    override suspend fun isUnsafe(blocks: List<Cluster.Block<out LogicImage>>): Boolean =
        withContext(ImperiumScope.IO.coroutineContext) {
            when (val result = analysis.isUnsafe(createImage(blocks))) {
                is ImageAnalysis.Result.Success -> result.unsafe
                is ImageAnalysis.Result.Failure -> {
                    logger.error("Failed to analyze image: {}", result.message)
                    false
                }
            }
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
                    output.setRGB(
                        pixel.key % image.resolution, pixel.key / image.resolution, pixel.value)
                }
                output = invertYAxis(output)
            }
            is LogicImage.Drawer -> {
                for (processor in image.processors) {
                    for (instruction in processor.instructions) {
                        when (instruction) {
                            is LogicImage.Drawer.Instruction.Color -> {
                                graphics.color =
                                    Color(
                                        instruction.r, instruction.g, instruction.b, instruction.a)
                            }
                            is LogicImage.Drawer.Instruction.Rect -> {
                                if (instruction.w == 1 && instruction.h == 1) {
                                    output.setRGB(instruction.x, instruction.y, graphics.color.rgb)
                                } else {
                                    graphics.fillRect(
                                        instruction.x, instruction.y, instruction.w, instruction.h)
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
