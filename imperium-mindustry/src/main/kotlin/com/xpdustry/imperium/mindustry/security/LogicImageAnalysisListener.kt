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
package com.xpdustry.imperium.mindustry.security

import arc.math.geom.Point2
import arc.struct.IntSet
import cloud.commandframework.kotlin.extension.buildAndRegister
import com.xpdustry.imperium.common.account.MindustryUUID
import com.xpdustry.imperium.common.account.UserManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.collection.findMostCommon
import com.xpdustry.imperium.common.geometry.Cluster
import com.xpdustry.imperium.common.geometry.ClusterManager
import com.xpdustry.imperium.common.image.LogicImage
import com.xpdustry.imperium.common.image.LogicImageAnalysis
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.toHexString
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.PunishmentMessage
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.history.BlockHistory
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import fr.xpdustry.distributor.api.event.EventHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.logic.LExecutor
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.logic.CanvasBlock
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.logic.LogicDisplay
import java.awt.Color
import java.time.Duration
import java.time.Instant
import java.util.Queue
import java.util.concurrent.PriorityBlockingQueue
import kotlin.time.Duration.Companion.seconds

// TODO
//  - Different cluster managers for different teams ?
//  - Index blocks on map load ?
class LogicImageAnalysisListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val analyzer = instances.get<LogicImageAnalysis>()
    private val history = instances.get<BlockHistory>()
    private val clientCommandManager = instances.get<ImperiumPluginCommandManager>("client")
    private val users = instances.get<UserManager>()
    private val punishments = instances.get<PunishmentManager>()

    private val drawerQueue = PriorityBlockingQueue<Wrapper<Cluster<LogicImage.Drawer>>>()
    private val displays = ClusterManager(QueueClusterListener(drawerQueue, ::filterDrawer, ::findDrawerAuthor))
    private lateinit var drawerJob: Job
    private val pixmapQueue = PriorityBlockingQueue<Wrapper<Cluster<LogicImage.PixMap>>>()
    private val canvases = ClusterManager(QueueClusterListener(pixmapQueue, ::filterPixMap, ::findPixMapAuthor))
    private lateinit var pixmapJob: Job

    private val debugPlayers = PlayerMap<Boolean>(instances.get())

    override fun onImperiumInit() {
        drawerJob = startProcessing(drawerQueue)
        pixmapJob = startProcessing(pixmapQueue)

        clientCommandManager.buildAndRegister("image-analysis") {
            literal("debug")
            commandDescription("Show the location of the clusters")
            handler {
                if (debugPlayers[it.sender.player] == true) {
                    debugPlayers.remove(it.sender.player)
                    it.sender.sendMessage("Image analysis debug mode is now disabled")
                } else {
                    debugPlayers[it.sender.player] = true
                    it.sender.sendMessage("Image analysis debug mode is now enabled")
                }
            }
        }

        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(1.seconds)
                runMindustryThread {
                    if (Vars.state.isPlaying) {
                        for (player in debugPlayers.entries.filter { it.second }.map { it.first }) {
                            renderCluster(player, displays, Color.PINK)
                            renderCluster(player, canvases, Color.YELLOW)
                        }
                    }
                }
            }
        }
    }

    override fun onImperiumExit() = runBlocking {
        drawerJob.cancel()
        pixmapJob.cancel()
        listOf(drawerJob, pixmapJob).joinAll()
    }

    private fun renderCluster(player: Player, manager: ClusterManager<*>, color: Color) {
        for ((index, cluster) in manager.clusters.withIndex()) {
            for (block in cluster.blocks) {
                Call.label(
                    player.con,
                    "[${color.toHexString()}]C$index",
                    1F,
                    block.x.toFloat() * Vars.tilesize,
                    block.y.toFloat() * Vars.tilesize,
                )
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: EventType.PlayerLeave) {
        debugPlayers.remove(event.player)
    }

    @EventHandler
    fun onWorldLoad(event: EventType.WorldLoadEvent) {
        displays.reset()
        canvases.reset()
    }

    @EventHandler
    fun onBlockBuildEvent(event: EventType.BlockBuildEndEvent) {
        if (event.unit == null || !event.unit.isPlayer) {
            return
        }

        var building = event.tile.build
        if (event.breaking && building is ConstructBlock.ConstructBuild && !building.prevBuild.isEmpty) {
            building = building.prevBuild.first()
        }

        if (building is LogicDisplay.LogicDisplayBuild) {
            if (event.breaking) {
                displays.removeElement(building.rx, building.ry)
                logger.trace("Removed display at ({}, {})", building.rx, building.ry)
                return
            }

            val processors = mutableListOf<LogicImage.Drawer.Processor>()
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
                        processors += LogicImage.Drawer.Processor(build.rx, build.ry, instructions)
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
                Cluster.Block(
                    building.rx,
                    building.ry,
                    building.block.size,
                    LogicImage.Drawer(
                        (building.block as LogicDisplay).displaySize,
                        processors,
                    ),
                ),
            )
        }

        if (building is CanvasBlock.CanvasBuild) {
            if (event.breaking) {
                canvases.removeElement(building.rx, building.ry)
                logger.trace("Removed canvas at ({}, {})", building.rx, building.ry)
                return
            }

            val config = event.config
            if (config !is ByteArray?) {
                return
            }

            val block = building.block as CanvasBlock
            val pixels = mutableMapOf<Int, Int>()
            val temp = arc.graphics.Color()

            if (config != null) {
                for (i in 0 until block.canvasSize * block.canvasSize) {
                    val bitOffset = i * block.bitsPerPixel
                    val pal = getByte(block, config, bitOffset)
                    temp.set(block.palette[pal])
                    pixels[i] = temp.rgb888()
                }
            } else {
                val color = temp.set(block.palette[0]).rgb888()
                for (i in 0 until block.canvasSize * block.canvasSize) {
                    pixels[i] = color
                }
            }

            canvases.addElement(
                Cluster.Block(
                    building.rx,
                    building.ry,
                    building.block.size,
                    LogicImage.PixMap(
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

    private fun readInstructions(executor: LExecutor): List<LogicImage.Drawer.Instruction> {
        val instructions = mutableListOf<LogicImage.Drawer.Instruction>()
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
                    LogicImage.Drawer.Instruction.Color(r, g, b, a)
                }
                LogicDisplay.commandRect -> {
                    val x = executor.numi(instruction.x)
                    val y = executor.numi(instruction.y)
                    val w = executor.numi(instruction.p1)
                    val h = executor.numi(instruction.p2)
                    LogicImage.Drawer.Instruction.Rect(x, y, w, h)
                }
                LogicDisplay.commandTriangle -> {
                    val x1 = executor.numi(instruction.x)
                    val y1 = executor.numi(instruction.y)
                    val x2 = executor.numi(instruction.p1)
                    val y2 = executor.numi(instruction.p2)
                    val x3 = executor.numi(instruction.p3)
                    val y3 = executor.numi(instruction.p4)
                    LogicImage.Drawer.Instruction.Triangle(x1, y1, x2, y2, x3, y3)
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

    private fun <T : LogicImage> startProcessing(queue: Queue<Wrapper<Cluster<T>>>) = ImperiumScope.MAIN.launch {
        while (isActive) {
            delay(1.seconds)
            val element = queue.peek()
            if (element == null || element.instant > Instant.now()) {
                continue
            }
            queue.remove()

            launch {
                logger.debug("Processing cluster ({}, {})", element.value.x, element.value.y)
                val (unsafe, entry) = analyzer.isUnsafe(element.value.blocks)
                if (unsafe) {
                    logger.debug("Cluster ({}, {}) is unsafe (entry={}). Destroying.", element.value.x, element.value.y, entry)

                    runMindustryThread {
                        for (block in element.value.blocks) {
                            Vars.world.tile(block.x, block.y)?.build?.kill()
                            val data = block.data
                            if (data is LogicImage.Drawer) {
                                for (processor in data.processors) {
                                    Vars.world.tile(processor.x, processor.y)?.build?.kill()
                                }
                            }
                        }
                    }

                    val extra = if (entry != null) PunishmentMessage.Extra.Nsfw(entry) else PunishmentMessage.Extra.None
                    val player = Groups.player.find { it.uuid() == element.author }
                    if (player != null) {
                        punishments.punish(
                            null,
                            Punishment.Target(player.ip().toInetAddress(), player.uuid()),
                            "Placing NSFW images",
                            Punishment.Type.BAN,
                            Duration.ofDays(3L),
                            extra,
                        )
                        return@launch
                    }

                    val address = users.findByUuid(element.author)?.lastAddress
                    if (address != null) {
                        punishments.punish(
                            null,
                            Punishment.Target(address, element.author),
                            "Placing NSFW images",
                            Punishment.Type.BAN,
                            Duration.ofDays(3L),
                            extra,
                        )
                        return@launch
                    }

                    logger.warn("Could not find player with UUID ${element.author}")
                } else {
                    logger.debug("Cluster ({}, {}) is safe", element.value.x, element.value.y)
                }
            }
        }
    }

    private fun filterDrawer(cluster: Cluster<LogicImage.Drawer>): Boolean =
        cluster.blocks.flatMap { it.data.processors }.flatMap { it.instructions }.size > 128

    private fun filterPixMap(cluster: Cluster<LogicImage.PixMap>): Boolean =
        cluster.blocks.size >= 9

    private fun findDrawerAuthor(cluster: Cluster<LogicImage.Drawer>): MindustryUUID? =
        cluster.blocks.flatMap { it.data.processors }
            .mapNotNull { processor -> history.getLatestPlace(processor.x, processor.y) }
            .filter { it.block is LogicBlock }
            .mapNotNull { it.author.uuid }
            .findMostCommon()

    private fun findPixMapAuthor(cluster: Cluster<LogicImage.PixMap>): MindustryUUID? =
        cluster.blocks.mapNotNull { block -> history.getLatestPlace(block.x, block.y) }
            .filter { it.block is CanvasBlock }
            .mapNotNull { it.author.uuid }
            .findMostCommon()

    // Goofy ass Mindustry coordinate system
    private val Building.rx: Int get() = tileX() + block.sizeOffset
    private val Building.ry: Int get() = tileY() + block.sizeOffset

    companion object {
        private const val MAX_RANGE = 32
        private val logger by LoggerDelegate()
    }

    private class QueueClusterListener<T : LogicImage>(
        private val queue: Queue<Wrapper<Cluster<T>>>,
        private val clusterFilter: (Cluster<T>) -> Boolean,
        private val clusterAuthor: (Cluster<T>) -> MindustryUUID?,
    ) : ClusterManager.Listener<T> {
        override fun onClusterEvent(cluster: Cluster<T>, event: ClusterManager.Event) {
            val removed = queue.removeIf { it.value.x == cluster.x && it.value.y == cluster.y }
            if (event == ClusterManager.Event.NEW || event == ClusterManager.Event.UPDATE) {
                if (!clusterFilter(cluster)) {
                    logger.trace("Cluster (${cluster.x}, ${cluster.y}) does not pass the filter")
                    return
                }
                val author = clusterAuthor(cluster)
                if (author == null) {
                    logger.trace("Cluster (${cluster.x}, ${cluster.y}) has no author")
                    return
                }
                queue.add(Wrapper(cluster.copy(), Instant.now().plusSeconds(5L), author))
                return
            }
            if (removed) {
                logger.trace("Removed cluster (${cluster.x}, ${cluster.y}) from queue")
            }
        }
    }

    internal data class Wrapper<T : Any>(
        val value: T,
        val instant: Instant,
        val author: MindustryUUID,
    ) : Comparable<Wrapper<T>> {
        override fun compareTo(other: Wrapper<T>): Int = instant.compareTo(other.instant)
    }
}
