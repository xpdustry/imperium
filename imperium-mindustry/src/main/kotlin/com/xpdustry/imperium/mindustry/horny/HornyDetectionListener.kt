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
import arc.struct.IntIntMap
import arc.struct.IntSet
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.misc.LoggerDelegate
import fr.xpdustry.distributor.api.event.EventHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.EventType
import mindustry.gen.Building
import mindustry.logic.LExecutor
import mindustry.world.blocks.logic.CanvasBlock
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.logic.LogicDisplay
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.time.Instant
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.imageio.ImageIO
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.Duration.Companion.seconds

class HornyDetectionListener : ImperiumApplication.Listener {

    private val lock = ReentrantReadWriteLock()
    private var counter = 0
    private val logicClusters = mutableListOf<DisplayCluster>()
    private var canvasClusters = mutableListOf<CanvasCluster>()

    override fun onImperiumInit() {
        MAX_RANGE = (Blocks.hyperProcessor as LogicBlock).range.toInt() / Vars.tilesize

        Vars.dataDirectory.child("nsfw").mkdirs()
        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(1.seconds)
                lock.read {
                    for (cluster in logicClusters) {
                        if (!cluster.requiresVerification()) {
                            continue
                        }
                        val image = createImage(cluster)
                        val output = Vars.dataDirectory.child("nsfw").child("image" + counter++ + ".png").file()
                        try {
                            ImageIO.write(image, "png", output)
                            image.graphics.dispose()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        cluster.verified()
                    }

                    for (cluster in canvasClusters) {
                        if (!cluster.requiresVerification()) {
                            continue
                        }
                        val image = createImage(cluster)
                        val output = Vars.dataDirectory.child("nsfw").child("image" + counter++ + ".png").file()
                        try {
                            ImageIO.write(image, "png", output)
                            image.graphics.dispose()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        cluster.verified()
                    }
                }
            }
        }
    }

    @EventHandler
    fun onWorldLoad(event: EventType.WorldLoadEvent) {
        lock.write {
            logicClusters.clear()
        }
    }

    @EventHandler
    fun onBlockBuildEvent(event: EventType.BlockBuildEndEvent) {
        (event.tile.build as? LogicDisplay.LogicDisplayBuild)?.let {
            onLogicDisplayBuild(it, event.breaking)
        }
        (event.tile.build as? CanvasBlock.CanvasBuild)?.let {
            (event.config as? ByteArray?)?.let { config ->
                onCanvasBuild(it, config, event.breaking)
            }
        }
    }

    private fun onCanvasBuild(building: CanvasBlock.CanvasBuild, config: ByteArray?, breaking: Boolean) {
        if (breaking) {
            ImperiumScope.MAIN.launch {
                lock.write {
                    for (i in canvasClusters.indices) {
                        for (canvas in canvasClusters[i].canvases) {
                            if (canvas.x == building.rx && canvas.y == building.ry) {
                                val cluster = canvasClusters.removeAt(i)
                                logger.trace("Display cluster removed at ${canvas.x} ${canvas.y}")
                                cluster.canvases.forEach { addCanvas(it) }
                                return@write
                            }
                        }
                    }
                }
            }
            return
        }

        if (config == null) {
            return
        }

        val block = building.block as CanvasBlock
        val pixels = IntIntMap()
        for (i in 0 until block.canvasSize * block.canvasSize) {
            val bitOffset = i * block.bitsPerPixel
            val pal = getByte(block, config, bitOffset)
            pixels.put(i, arc.graphics.Color(block.palette[pal]).rgb888())
        }

        val canvas = Canvas(
            building.rx,
            building.ry,
            building.block.size,
            block.canvasSize,
            pixels,
        )

        ImperiumScope.MAIN.launch {
            lock.write {
                addCanvas(canvas)
            }
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

    private fun addCanvas(canvas: Canvas) {
        val candidates = mutableListOf<CanvasCluster>()
        for (cluster in canvasClusters) {
            if (cluster.canBePartOfCluster(canvas)) {
                candidates += cluster
            }
        }
        if (candidates.isEmpty()) {
            canvasClusters += CanvasCluster(canvas)
            logger.trace("Display cluster created at ${canvas.x} ${canvas.y}")
        } else if (candidates.size == 1) {
            candidates[0].canvases += canvas
            candidates[0].update()
            logger.trace("Display cluster updated at ${canvas.x} ${canvas.y}")
        } else {
            val cluster = CanvasCluster(canvas)
            for (candidate in candidates) {
                cluster.canvases += candidate.canvases
                canvasClusters.remove(candidate)
            }
            cluster.update()
            canvasClusters += cluster
            logger.trace("Display cluster merged at ${canvas.x} ${canvas.y}")
        }
    }

    private fun createImage(cluster: CanvasCluster): BufferedImage {
        val image = BufferedImage(cluster.w * RES_PER_BLOCK, cluster.h * RES_PER_BLOCK, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.graphics
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, image.width, image.height)

        for (canvas in cluster.canvases) {
            graphics.drawImage(
                createImage(canvas),
                (canvas.x - cluster.x) * RES_PER_BLOCK,
                (canvas.y - cluster.y) * RES_PER_BLOCK,
                canvas.size * RES_PER_BLOCK,
                canvas.size * RES_PER_BLOCK,
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

    private fun createImage(canvas: Canvas): BufferedImage {
        val image = BufferedImage(canvas.resolution, canvas.resolution, BufferedImage.TYPE_INT_RGB)
        val graphics = image.graphics
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, image.width, image.height)

        for (pixel in canvas.pixels) {
            image.setRGB(pixel.key % canvas.resolution, pixel.key / canvas.resolution, pixel.value)
        }

        graphics.dispose()
        return image
    }

    private fun onLogicDisplayBuild(building: LogicDisplay.LogicDisplayBuild, breaking: Boolean) {
        if (breaking) {
            ImperiumScope.MAIN.launch {
                lock.write {
                    for (i in logicClusters.indices) {
                        for (display in logicClusters[i].displays) {
                            if (display.x == building.rx && display.y == building.ry) {
                                val cluster = logicClusters.removeAt(i)
                                logger.trace("Display cluster removed at ${display.x} ${display.y}")
                                cluster.displays.forEach { addDisplay(it) }
                                return@write
                            }
                        }
                    }
                }
            }
            return
        }

        val drawers = mutableListOf<Drawer>()
        val covered = IntSet()
        for (x in (building.tileX() - MAX_RANGE)..(building.tileX() + MAX_RANGE)) {
            for (y in (building.tileY() - MAX_RANGE)..(building.tileY() + MAX_RANGE)) {
                val build = Vars.world.tile(x, y)?.build as? LogicBlock.LogicBuild ?: continue
                if (!covered.add(Point2.pack(x, y)) || build.links.find { it.active && Vars.world.tile(it.x, it.y).build == building } == null || build.executor.instructions.none { it is LExecutor.DrawFlushI }) {
                    continue
                }
                val instructions = readInstructions(build.executor)
                if (instructions.isNotEmpty()) {
                    drawers += Drawer(build.rx, build.ry, instructions)
                }
                build.tile.getLinkedTiles {
                    covered.add(Point2.pack(it.x.toInt(), it.y.toInt()))
                }
            }
        }

        if (drawers.isEmpty()) {
            return
        }

        val display = Display(
            building.rx,
            building.ry,
            building.block.size,
            (building.block as LogicDisplay).displaySize,
            (building.block as LogicDisplay).displaySize,
            drawers,
        )

        ImperiumScope.MAIN.launch {
            lock.write {
                addDisplay(display)
            }
        }
    }

    private fun addDisplay(display: Display) {
        val candidates = mutableListOf<DisplayCluster>()
        for (cluster in logicClusters) {
            if (cluster.canBePartOfCluster(display)) {
                candidates += cluster
            }
        }
        if (candidates.isEmpty()) {
            logicClusters += DisplayCluster(display)
            logger.trace("Display cluster created at ${display.x} ${display.y}")
        } else if (candidates.size == 1) {
            candidates[0].displays += display
            candidates[0].update()
            logger.trace("Display cluster updated at ${display.x} ${display.y}")
        } else {
            val cluster = DisplayCluster(display)
            for (candidate in candidates) {
                cluster.displays += candidate.displays
                logicClusters.remove(candidate)
            }
            cluster.update()
            logicClusters += cluster
            logger.trace("Display cluster merged at ${display.x} ${display.y}")
        }
    }

    private fun readInstructions(executor: LExecutor): List<DrawerInstruction> {
        val instructions = mutableListOf<DrawerInstruction>()
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
                    DrawerInstruction.Color(r, g, b, a)
                }
                LogicDisplay.commandRect -> {
                    val x = executor.numi(instruction.x)
                    val y = executor.numi(instruction.y)
                    val w = executor.numi(instruction.p1)
                    val h = executor.numi(instruction.p2)
                    DrawerInstruction.Rect(x, y, w, h)
                }
                LogicDisplay.commandTriangle -> {
                    val x1 = executor.numi(instruction.x)
                    val y1 = executor.numi(instruction.y)
                    val x2 = executor.numi(instruction.p1)
                    val y2 = executor.numi(instruction.p2)
                    val x3 = executor.numi(instruction.p3)
                    val y3 = executor.numi(instruction.p4)
                    DrawerInstruction.Triangle(x1, y1, x2, y2, x3, y3)
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

    // Creates a bigger images from the displays within the cluster
    private fun createImage(cluster: DisplayCluster): BufferedImage {
        val image = BufferedImage(cluster.w * RES_PER_BLOCK, cluster.h * RES_PER_BLOCK, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.graphics
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, image.width, image.height)

        for (display in cluster.displays) {
            graphics.drawImage(
                createImage(display),
                (display.x - cluster.x) * RES_PER_BLOCK,
                (display.y - cluster.y) * RES_PER_BLOCK,
                display.size * RES_PER_BLOCK,
                display.size * RES_PER_BLOCK,
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

    // Creates an image from the given display (warning: The Y axis needs to be inverted)
    private fun createImage(cluster: Display): BufferedImage {
        val image = BufferedImage(cluster.rw, cluster.rh, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.graphics
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, image.width, image.height)

        for (drawer in cluster.drawers) {
            for (instruction in drawer.instructions) {
                when (instruction) {
                    is DrawerInstruction.Color -> {
                        graphics.color = Color(instruction.r, instruction.g, instruction.b, instruction.a)
                    }
                    is DrawerInstruction.Rect -> {
                        if (instruction.w == 1 && instruction.h == 1) {
                            image.setRGB(instruction.x, instruction.y, graphics.color.rgb)
                        } else {
                            graphics.fillRect(instruction.x, instruction.y, instruction.w, instruction.h)
                        }
                    }
                    is DrawerInstruction.Triangle -> {
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

    // Goofy ass Mindustry coordinate system
    private val Building.rx: Int get() = tileX() + block.sizeOffset
    private val Building.ry: Int get() = tileY() + block.sizeOffset

    companion object {
        private const val RES_PER_BLOCK = 30
        private var MAX_RANGE = -1
        private val logger by LoggerDelegate()
    }

    // TODO Combine both classes
    @Suppress("DuplicatedCode")
    private data class CanvasCluster(var x: Int, var y: Int, var w: Int, var h: Int, val canvases: MutableList<Canvas>) {
        constructor(initial: Canvas) : this(initial.x, initial.y, initial.size, initial.size, mutableListOf(initial))

        private var verification: Instant? = Instant.now().plusSeconds(5L)

        fun update() {
            x = canvases.minOf { it.x }
            y = canvases.minOf { it.y }
            w = canvases.maxOf { it.x + it.size } - x
            h = canvases.maxOf { it.y + it.size } - y
            verification = Instant.now().plusSeconds(5L)
        }

        fun requiresVerification() = verification != null && Instant.now().isAfter(verification)

        fun verified() {
            verification = null
        }

        // Check if a display share a side with another (sharing a single corner does not count)
        fun canBePartOfCluster(display: Canvas): Boolean = canvases.any {
            val x1 = display.x
            val y1 = display.y
            val s1 = display.size
            val x2 = it.x
            val y2 = it.y
            val s2 = it.size
            (x1 == x2 && y1 + s1 == y2) || (x1 == x2 && y1 - s2 == y2) || (x1 + s1 == x2 && y1 == y2) || (x1 - s2 == x2 && y1 == y2)
        }
    }

    private data class Canvas(val x: Int, val y: Int, val size: Int, val resolution: Int, val pixels: IntIntMap)

    @Suppress("DuplicatedCode")
    private data class DisplayCluster(var x: Int, var y: Int, var w: Int, var h: Int, val displays: MutableList<Display>) {
        constructor(initial: Display) : this(initial.x, initial.y, initial.size, initial.size, mutableListOf(initial))

        private var verification: Instant? = Instant.now().plusSeconds(5L)

        fun update() {
            x = displays.minOf { it.x }
            y = displays.minOf { it.y }
            w = displays.maxOf { it.x + it.size } - x
            h = displays.maxOf { it.y + it.size } - y
            verification = Instant.now().plusSeconds(5L)
        }

        fun requiresVerification() = verification != null && Instant.now().isAfter(verification)

        fun verified() {
            verification = null
        }

        // TODO Use rectangle overlap algorithm instead of this shit
        // Check if a display share a side with another (sharing a single corner does not count)
        fun canBePartOfCluster(display: Display): Boolean = displays.any {
            val x1 = display.x
            val y1 = display.y
            val s1 = display.size
            val x2 = it.x
            val y2 = it.y
            val s2 = it.size
            (x1 == x2 && y1 + s1 == y2) || (x1 == x2 && y1 - s2 == y2) || (x1 + s1 == x2 && y1 == y2) || (x1 - s2 == x2 && y1 == y2)
        }
    }

    private data class Display(val x: Int, val y: Int, val size: Int, val rw: Int, val rh: Int, val drawers: List<Drawer>)

    private data class Drawer(val x: Int, val y: Int, val instructions: List<DrawerInstruction>)

    private sealed interface DrawerInstruction {
        data class Color(val r: Int, val g: Int, val b: Int, val a: Int) : DrawerInstruction
        data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) : DrawerInstruction
        data class Triangle(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val x3: Int, val y3: Int) : DrawerInstruction
    }
}
