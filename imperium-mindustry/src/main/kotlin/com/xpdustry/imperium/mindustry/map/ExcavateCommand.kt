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
package com.xpdustry.imperium.mindustry.map

import arc.math.Mathf
import cloud.commandframework.kotlin.extension.commandBuilder
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.command.SimpleVoteManager
import com.xpdustry.imperium.mindustry.command.VoteManager
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.registerCopy
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import fr.xpdustry.distributor.api.event.EventHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Iconc
import mindustry.gen.Player
import mindustry.gen.Sounds
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import arc.graphics.Color as ArcColor

class ExcavateCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val clientCommandManager = instances.get<ImperiumPluginCommandManager>("client")
    private val areas = PlayerMap<ExcavateArea>(instances.get())
    private val pitchSequence = AtomicInteger(0)
    private val config = instances.get<ServerConfig.Mindustry>()
    private lateinit var renderer: Job
    private val manager = SimpleVoteManager(
        plugin = instances.get(),
        duration = 1.minutes,
        finished = {
            if (it.status == VoteManager.Session.Status.SUCCESS) {
                excavate(it.target)
            } else {
                Call.sendMessage("The excavation has failed!")
            }
        },
        threshold = {
            when (Groups.player.size()) {
                0 -> 0
                1 -> 1
                2, 3, 4, 5 -> 2
                6, 7, 8, 9 -> 3
                else -> 4
            }
        },
    )

    override fun onImperiumInit() {
        clientCommandManager.commandBuilder("excavate", aliases = arrayOf("e")) {
            registerCopy("begin") {
                handler { ctx ->
                    if (areas[ctx.sender.player] != null) {
                        ctx.sender.sendMessage("You have already started setting points!")
                        return@handler
                    }
                    ctx.sender.sendMessage("You have started setting points! Tap on the first point.")
                    areas[ctx.sender.player] = ExcavateArea()
                }
            }

            registerCopy("start") {
                handler { ctx ->
                    val area = areas[ctx.sender.player]
                    if (area == null) {
                        ctx.sender.sendMessage("You haven't started setting points yet!")
                        return@handler
                    }
                    if (area.p1 == UNSET_POINT || area.p2 == UNSET_POINT) {
                        ctx.sender.sendMessage("You have not set both points yet!")
                        return@handler
                    }
                    if (manager.session != null) {
                        ctx.sender.sendMessage("There is already an excavation in progress!")
                    } else {
                        val session = manager.start(ctx.sender.player, true, area)
                        areas.remove(ctx.sender.player)
                        Call.sendMessage(
                            """
                            ${ctx.sender.player.name} has started an excavation at between (${area.p1.x}, ${area.p1.y}) and (${area.p2.x}, ${area.p2.y})!
                            Vote using [accent]/excavate y[] or [accent]/excavate n[]. [accent]${session.remaining}[] votes are required to pass.
                            """.trimIndent(),
                        )
                    }
                }
            }

            registerCopy("yes", aliases = arrayOf("y")) {
                handler { ctx -> vote(ctx.sender.player, manager.session, true) }
            }

            registerCopy("no", aliases = arrayOf("n")) {
                handler { ctx -> vote(ctx.sender.player, manager.session, false) }
            }

            registerCopy("cancel", aliases = arrayOf("c")) {
                handler { ctx ->
                    val area = areas[ctx.sender.player]
                    if (area != null) {
                        areas.remove(ctx.sender.player)
                        ctx.sender.sendMessage("You have cancelled setting points!")
                        return@handler
                    }
                    if (manager.session == null) {
                        ctx.sender.sendMessage("There is no excavation in progress!")
                        return@handler
                    }
                    if (ctx.sender.player.admin) {
                        manager.session!!.failure()
                        Call.sendMessage("${ctx.sender.player.name} has cancelled the excavation.")
                    } else {
                        ctx.sender.sendMessage("You are not allowed to cancel the excavation!")
                    }
                }
            }
        }

        renderer = ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(1.seconds)
                if (Vars.state.isPlaying) {
                    renderZones()
                }
            }
        }
    }

    override fun onImperiumExit() = runBlocking(ImperiumScope.MAIN.coroutineContext) {
        renderer.cancelAndJoin()
    }

    private suspend fun excavate(area: ExcavateArea) {
        Call.sendMessage("The excavation has passed! The area between (${area.x1}, ${area.y1}) and (${area.x2}, ${area.y2}) will be excavated.")
        for (y in area.y1..area.y2) {
            delay(100.milliseconds)
            runMindustryThread {
                for (x in area.x1..area.x2) {
                    val tile = Vars.world.tile(x, y)
                    val block = tile.block()
                    if (block == null || !block.isStatic) {
                        continue
                    }
                    Call.setTile(tile, Blocks.air, Team.derelict, 0)
                    Call.setFloor(tile, getFloorOfWall(block), Blocks.air)
                    Call.effect(Fx.flakExplosion, x.toFloat() * Vars.tilesize, y.toFloat() * Vars.tilesize, 0F, ArcColor.white)
                    Call.soundAt(Sounds.place, x.toFloat() * Vars.tilesize, y.toFloat() * Vars.tilesize, 1F, getNextPitch())
                }
            }
        }
        Call.sendMessage("The excavation has finished!")
    }

    private fun renderZones() {
        for (entry in areas.entries) {
            val p1 = entry.second.p1
            if (p1 != UNSET_POINT) {
                Call.label(
                    entry.first.con(),
                    Iconc.alphaaaa.toString(),
                    1F,
                    p1.x.toFloat() * Vars.tilesize,
                    p1.y.toFloat() * Vars.tilesize,
                )
            }
            val p2 = entry.second.p2
            if (p2 != UNSET_POINT) {
                Call.label(
                    entry.first.con(),
                    Iconc.alphaaaa.toString(),
                    1F,
                    p2.x.toFloat() * Vars.tilesize,
                    p2.y.toFloat() * Vars.tilesize,
                )
            }
        }

        val area = manager.session?.target ?: return
        for (y in area.y1..area.y2) {
            for (x in area.x1..area.x2) {
                if (x == area.x1 || x == area.x2 || y == area.y1 || y == area.y2) {
                    Call.label(
                        Iconc.alphaaaa.toString(),
                        1F,
                        x.toFloat() * Vars.tilesize,
                        y.toFloat() * Vars.tilesize,
                    )
                }
            }
        }
        val mx = area.x1 + ((area.x2 - area.x1) / 2)
        val my = area.y1 + ((area.y2 - area.y1) / 2)
        val half = Vars.tilesize / 2F
        Call.label("EXCAVATION SITE", 1F, mx.toFloat() * Vars.tilesize + half, my.toFloat() * Vars.tilesize + half)
    }

    @EventHandler
    internal fun onTapEvent(event: EventType.TapEvent) {
        val area = areas[event.player] ?: return
        val point = ImmutablePoint(event.tile.x.toInt(), event.tile.y.toInt())
        if (point.x !in 0..Vars.world.width() || point.y !in 0..Vars.world.height()) {
            event.player.sendMessage("The chosen excavate point is out of bounds, try again!")
            return
        }

        val other = if (area.first) area.p2 else area.p1
        if (other != UNSET_POINT) {
            val dx = abs(point.x - other.x)
            val dy = abs(point.y - other.y)
            if (dx >= config.world.maxExcavateSize || dy >= config.world.maxExcavateSize) {
                areas[event.player] = ExcavateArea()
                event.player.sendMessage("The chosen excavate point is too far from the other point, try again!")
                return
            }
            if (dx == 0 || dy == 0) {
                areas[event.player] = ExcavateArea()
                event.player.sendMessage("The chosen excavate point is on the same axis as the other point, try again!")
                return
            }
        }

        val adjective: String
        if (area.first) {
            areas[event.player] = area.copy(p1 = point, first = false)
            adjective = "first"
        } else {
            areas[event.player] = area.copy(p2 = point, first = true)
            adjective = "second"
        }

        event.player.sendMessage("You set the $adjective point to (${point.x}, ${point.y})")
    }

    private fun vote(player: Player, session: VoteManager.Session<ExcavateArea>?, value: Boolean) {
        if (session == null) {
            player.sendMessage("There is no excavation in progress!")
            return
        }
        if (session.getVote(player) != null) {
            player.sendMessage("You have already voted!")
            return
        }
        Call.sendMessage("${player.name} has voted ${if (value) "yes" else "no"}! ${session.remaining} more votes are required to pass.")
        session.setVote(player, value)
    }

    private fun getNextPitch(): Float {
        pitchSequence.incrementAndGet()
        if (pitchSequence.get() > 30) {
            pitchSequence.set(0)
        }
        return 1f + Mathf.clamp(pitchSequence.get() / 30f) * 1.9f
    }

    private fun getFloorOfWall(block: mindustry.world.Block) = when (block) {
        Blocks.stoneWall -> Blocks.stone
        Blocks.sandWall -> Blocks.sand
        Blocks.iceWall -> Blocks.ice
        Blocks.snowWall -> Blocks.snow
        Blocks.dirtWall -> Blocks.dirt
        Blocks.shaleWall -> Blocks.shale
        else -> Blocks.stone
    }

    data class ExcavateArea(val p1: ImmutablePoint = UNSET_POINT, val p2: ImmutablePoint = UNSET_POINT, val first: Boolean = true) {
        val x1 = min(p1.x, p2.x)
        val x2 = max(p1.x, p2.x)
        val y1 = min(p1.y, p2.y)
        val y2 = max(p1.y, p2.y)
    }

    companion object {
        private val UNSET_POINT = ImmutablePoint(-1, -1)
    }
}
