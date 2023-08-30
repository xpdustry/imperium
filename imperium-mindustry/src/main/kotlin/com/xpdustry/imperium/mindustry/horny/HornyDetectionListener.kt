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
package com.xpdustry.imperium.mindustry.horny

import arc.math.geom.Point2
import arc.struct.IntSet
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.mindustry.misc.BlockClusterManager
import com.xpdustry.imperium.mindustry.misc.Cluster
import com.xpdustry.imperium.mindustry.misc.ClusterBlock
import fr.xpdustry.distributor.api.event.EventHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Building
import mindustry.gen.Groups
import mindustry.logic.LExecutor
import mindustry.world.blocks.logic.CanvasBlock
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.logic.LogicDisplay
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.time.Instant
import java.util.Queue
import java.util.concurrent.PriorityBlockingQueue
import kotlin.time.Duration.Companion.seconds

class HornyDetectionListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val analyzer = instances.get<ImageAnalyzer>()
    private val drawerQueue = PriorityBlockingQueue<DelayedWrapper<Cluster<ImagePayload.Drawer>>>()
    private val pixmapQueue = PriorityBlockingQueue<DelayedWrapper<Cluster<ImagePayload.PixMap>>>()
    private val displays = BlockClusterManager { onClusterUpdate(drawerQueue, it) }
    private val canvases = BlockClusterManager { onClusterUpdate(pixmapQueue, it) }

    override fun onImperiumInit() {
        startProcessing(drawerQueue) { cluster -> createImage(cluster, ::createDrawerImage) }
        startProcessing(pixmapQueue) { cluster -> createImage(cluster, ::createPixMapImage) }
    }

    private fun <T : ImagePayload> startProcessing(
        queue: Queue<DelayedWrapper<Cluster<T>>>,
        renderer: (Cluster<T>) -> BufferedImage,
    ) = ImperiumScope.MAIN.launch {
        while (isActive) {
            delay(1.seconds)
            val element = queue.peek()
            if (element == null || element.instant > Instant.now()) {
                continue
            }
            queue.remove()
            launch {
                logger.debug("Processing cluster (${element.value.x}, ${element.value.y})")
                val image = renderer(element.value)
                if (analyzer.analyze(image) == ImageAnalyzer.Result.NSFW) {
                    val author = element.value.blocks
                        .groupingBy { it.builder }
                        .eachCount()
                        .maxBy { it.value }.key
                    Groups.player.find { it.ip().equals(author.hostAddress) }?.sendMessage(
                        "You dirty horny bastard, stop sending NSFW images to the server!",
                    )
                }
            }
        }
    }

    private fun <T : ImagePayload> onClusterUpdate(queue: Queue<DelayedWrapper<Cluster<T>>>, cluster: Cluster<T>) {
        val removed = queue.removeIf { it.value.x == cluster.x && it.value.y == it.value.y }
        queue.add(DelayedWrapper(cluster.copy(), Instant.now().plusSeconds(5L)))
        if (removed) {
            logger.trace("Delayed cluster (${cluster.x}, ${cluster.y}) processing")
        } else {
            logger.trace("Scheduled cluster (${cluster.x}, ${cluster.y}) for processing")
        }
    }

    @EventHandler
    fun onWorldLoad(event: EventType.WorldLoadEvent) {
        displays.reset()
        canvases.reset()
    }

    @EventHandler
    fun onBlockBuildEvent(event: EventType.BlockBuildEndEvent) {
        if (!event.unit.isPlayer) {
            return
        }

        val building = event.tile.build

        if (building is LogicDisplay.LogicDisplayBuild) {
            if (event.breaking) {
                displays.removeElement(building.rx, building.ry)
                return
            }

            val processors = mutableListOf<ImagePayload.Drawer.Processor>()
            val covered = IntSet()
            for (x in (building.tileX() - MAX_RANGE)..(building.tileX() + MAX_RANGE)) {
                for (y in (building.tileY() - MAX_RANGE)..(building.tileY() + MAX_RANGE)) {
                    val build = Vars.world.tile(x, y)?.build as? LogicBlock.LogicBuild ?: continue
                    if (!covered.add(Point2.pack(x, y)) || build.links.find {
                            it.active && Vars.world.tile(
                                it.x,
                                it.y,
                            ).build == building
                        } == null || build.executor.instructions.none { it is LExecutor.DrawFlushI }
                    ) {
                        continue
                    }
                    val instructions = readInstructions(build.executor)
                    if (instructions.isNotEmpty()) {
                        processors += ImagePayload.Drawer.Processor(build.rx, build.ry, instructions)
                    }
                    build.tile.getLinkedTiles {
                        covered.add(Point2.pack(it.x.toInt(), it.y.toInt()))
                    }
                }
            }

            if (processors.isEmpty()) {
                return
            }

            displays.addElement(
                ClusterBlock(
                    building.rx,
                    building.ry,
                    building.block.size,
                    event.unit.player.ip().toInetAddress(),
                    ImagePayload.Drawer(
                        (building.block as LogicDisplay).displaySize,
                        processors,
                    ),
                ),
            )
        }

        if (building is CanvasBlock.CanvasBuild) {
            if (event.breaking) {
                canvases.removeElement(building.rx, building.ry)
                return
            }

            val config = event.config
            if (config !is ByteArray?) {
                return
            }

            val block = building.block as CanvasBlock
            val pixels = mutableMapOf<Int, Int>()
            val temp = arc.graphics.Color()
            for (i in 0 until block.canvasSize * block.canvasSize) {
                val bitOffset = i * block.bitsPerPixel
                val pal = getByte(block, config, bitOffset)
                temp.set(block.palette[pal])
                pixels[i] = temp.rgb888()
            }

            canvases.addElement(
                ClusterBlock(
                    building.rx,
                    building.ry,
                    building.block.size,
                    event.unit.player.ip().toInetAddress(),
                    ImagePayload.PixMap(
                        block.canvasSize,
                        pixels,
                    ),
                ),
            )
        }
    }

    private fun getByte(block: CanvasBlock, data: ByteArray, bitOffset: Int): Int {
        var result = 0
        for (i in 0 until block.bitsPerPixel) {
            val word = i + bitOffset ushr 3
            result = result or ((if (data[word].toInt() and (1 shl (i + bitOffset and 7)) == 0) 0 else 1) shl i)
        }
        return result
    }

    private fun readInstructions(executor: LExecutor): List<ImagePayload.Drawer.Instruction> {
        val instructions = mutableListOf<ImagePayload.Drawer.Instruction>()
        for (instruction in executor.instructions) {
            if (instruction !is LExecutor.DrawI) {
                continue
            }
            instructions += when (instruction.type) {
                LogicDisplay.commandColor -> {
                    val r = normalizeColorValue(executor.numi(instruction.x))
                    val g = normalizeColorValue(executor.numi(instruction.y))
                    val b = normalizeColorValue(executor.numi(instruction.p1))
                    val a = normalizeColorValue(executor.numi(instruction.p2))
                    ImagePayload.Drawer.Instruction.Color(r, g, b, a)
                }
                LogicDisplay.commandRect -> {
                    val x = executor.numi(instruction.x)
                    val y = executor.numi(instruction.y)
                    val w = executor.numi(instruction.p1)
                    val h = executor.numi(instruction.p2)
                    ImagePayload.Drawer.Instruction.Rect(x, y, w, h)
                }
                LogicDisplay.commandTriangle -> {
                    val x1 = executor.numi(instruction.x)
                    val y1 = executor.numi(instruction.y)
                    val x2 = executor.numi(instruction.p1)
                    val y2 = executor.numi(instruction.p2)
                    val x3 = executor.numi(instruction.p3)
                    val y3 = executor.numi(instruction.p4)
                    ImagePayload.Drawer.Instruction.Triangle(x1, y1, x2, y2, x3, y3)
                }
                else -> continue
            }
        }
        return instructions
    }

    private fun normalizeColorValue(value: Int): Int {
        val result = value % 256
        return if (result < 0) result + 256 else result
    }

    private fun <T : ImagePayload> createImage(cluster: Cluster<T>, part: (T) -> BufferedImage): BufferedImage {
        val image = BufferedImage(cluster.w * RES_PER_BLOCK, cluster.h * RES_PER_BLOCK, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.graphics
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, image.width, image.height)

        for (block in cluster.blocks) {
            graphics.drawImage(
                part(block.data),
                (block.x - cluster.x) * RES_PER_BLOCK,
                (block.y - cluster.y) * RES_PER_BLOCK,
                block.size * RES_PER_BLOCK,
                block.size * RES_PER_BLOCK,
                null,
            )
        }

        // Invert y-axis, because mindustry uses bottom-left as origin
        val transform = AffineTransform.getScaleInstance(1.0, -1.0)
        transform.translate(0.0, -image.getHeight(null).toDouble())
        val op = AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR)
        val inverted = op.filter(image, null)
        graphics.dispose()

        return inverted
    }

    private fun createDrawerImage(payload: ImagePayload.Drawer): BufferedImage {
        val image = BufferedImage(payload.resolution, payload.resolution, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.graphics
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, image.width, image.height)

        for (processor in payload.processors) {
            for (instruction in processor.instructions) {
                when (instruction) {
                    is ImagePayload.Drawer.Instruction.Color -> {
                        graphics.color = Color(instruction.r, instruction.g, instruction.b, instruction.a)
                    }

                    is ImagePayload.Drawer.Instruction.Rect -> {
                        if (instruction.w == 1 && instruction.h == 1) {
                            image.setRGB(instruction.x, instruction.y, graphics.color.rgb)
                        } else {
                            graphics.fillRect(instruction.x, instruction.y, instruction.w, instruction.h)
                        }
                    }

                    is ImagePayload.Drawer.Instruction.Triangle -> {
                        graphics.fillPolygon(
                            intArrayOf(instruction.x1, instruction.x2, instruction.x3),
                            intArrayOf(instruction.y1, instruction.y2, instruction.y3),
                            3,
                        )
                    }
                }
            }
        }

        graphics.dispose()
        return image
    }

    private fun createPixMapImage(payload: ImagePayload.PixMap): BufferedImage {
        val image = BufferedImage(payload.resolution, payload.resolution, BufferedImage.TYPE_INT_RGB)
        val graphics = image.graphics
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, image.width, image.height)

        for (pixel in payload.pixels) {
            image.setRGB(pixel.key % payload.resolution, pixel.key / payload.resolution, pixel.value)
        }

        graphics.dispose()
        return image
    }

    // Goofy ass Mindustry coordinate system
    private val Building.rx: Int get() = tileX() + block.sizeOffset
    private val Building.ry: Int get() = tileY() + block.sizeOffset

    companion object {
        private const val RES_PER_BLOCK = 30
        private val MAX_RANGE = minOf(32, Vars.content.blocks().maxOf { (it as? LogicBlock)?.range ?: 0F }.toInt() / Vars.tilesize)
        private val logger by LoggerDelegate()
    }

    private sealed interface ImagePayload {
        data class PixMap(val resolution: Int, val pixels: Map<Int, Int>) : ImagePayload
        data class Drawer(val resolution: Int, val processors: List<Processor>) : ImagePayload {
            data class Processor(val x: Int, val y: Int, val instructions: List<Instruction>)
            sealed interface Instruction {
                data class Color(val r: Int, val g: Int, val b: Int, val a: Int) : Instruction
                data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) : Instruction
                data class Triangle(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val x3: Int, val y3: Int) : Instruction
            }
        }
    }

    private data class DelayedWrapper<T : Any>(val value: T, val instant: Instant) : Comparable<DelayedWrapper<T>> {
        override fun compareTo(other: DelayedWrapper<T>): Int = instant.compareTo(other.instant)
    }
}
