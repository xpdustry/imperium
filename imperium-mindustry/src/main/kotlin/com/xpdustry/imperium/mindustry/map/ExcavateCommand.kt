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
import cloud.commandframework.ArgumentDescription
import cloud.commandframework.kotlin.MutableCommandBuilder
import cloud.commandframework.kotlin.extension.commandBuilder
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.command.VoteManager
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import com.xpdustry.imperium.mindustry.misc.PlayerMap
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
import mindustry.gen.Iconc
import mindustry.gen.Player
import mindustry.gen.Sounds
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import arc.graphics.Color as ArcColor

class ExcavateCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val clientCommandManager = instances.get<ImperiumPluginCommandManager>("client")
    private val zones = PlayerMap<ExcavateZone>(instances.get())
    private lateinit var renderer: Job
    private val manager = VoteManager(
        duration = 1.minutes,
        success = ::scheduleExcavation,
        failure = {
            Call.sendMessage("The excavation has failed!")
        },
        _requiredVotes = {
            when (it.size) {
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
                    if (zones[ctx.sender.player] != null) {
                        ctx.sender.sendMessage("You have already started setting points!")
                        return@handler
                    }
                    ctx.sender.sendMessage("You have started setting points! Tap on the first point.")
                    zones[ctx.sender.player] = ExcavateZone()
                }
            }

            registerCopy("start") {
                handler { ctx ->
                    val zone = zones[ctx.sender.player]
                    if (zone == null) {
                        ctx.sender.sendMessage("You haven't started setting points yet!")
                        return@handler
                    }
                    if (zone.p1 == null || zone.p2 == null) {
                        ctx.sender.sendMessage("You have not set both points yet!")
                        return@handler
                    }

                    if (manager.current != null) {
                        ctx.sender.sendMessage("There is already an excavation in progress!")
                        return@handler
                    } else {
                        val session = manager.start(zone)
                        session.vote(ctx.sender.player, 1)
                        zones.remove(ctx.sender.player)
                        Call.sendMessage(
                            """
                            ${ctx.sender.player.name} has started an excavation at between (${zone.p1.x}, ${zone.p1.y}) and (${zone.p2.x}, ${zone.p2.y})!
                            Vote using [accent]/excavate y[] or [accent]/excavate n[]. [accent]${session.remainingVotes}[] votes are required to pass.
                            """,
                        )
                    }
                }
            }

            fun vote(player: Player, session: VoteManager<ExcavateZone>.Session?, value: Int) {
                if (session == null) {
                    player.sendMessage("There is no excavation in progress!")
                    return
                }
                if (!session.canVote(player)) {
                    player.sendMessage("You have already voted!")
                    return
                }
                session.vote(player, value)
                Call.sendMessage("${player.name} has voted ${if (value > 0) "yes" else "no"}! ${session.remainingVotes} votes are required to pass.")
            }

            registerCopy("yes", aliases = arrayOf("y")) {
                handler { ctx -> vote(ctx.sender.player, manager.current, 1) }
            }

            registerCopy("no", aliases = arrayOf("n")) {
                handler { ctx -> vote(ctx.sender.player, manager.current, -1) }
            }

            registerCopy("cancel", aliases = arrayOf("c")) {
                handler { ctx ->
                    val zone = zones[ctx.sender.player]
                    if (zone != null) {
                        zones.remove(ctx.sender.player)
                        ctx.sender.sendMessage("You have cancelled setting points!")
                        return@handler
                    }
                    if (manager.current == null) {
                        ctx.sender.sendMessage("There is no excavation in progress!")
                        return@handler
                    }
                    if (ctx.sender.player.admin) {
                        manager.cancel()
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

    private suspend fun scheduleExcavation(zone: ExcavateZone) {
        val x1 = minOf(zone.p1!!.x, zone.p2!!.x)
        val x2 = maxOf(zone.p1.x, zone.p2.x)
        val y1 = minOf(zone.p1.y, zone.p2.y)
        val y2 = maxOf(zone.p1.y, zone.p2.y)
        Call.sendMessage("The excavation has passed! The area between ($x1, $y1) and ($x2, $y2) will be excavated in 10 seconds.")
        for (y in y1..y2) {
            delay(100.milliseconds)
            runMindustryThread {
                for (x in x1..x2) {
                    val tile = Vars.world.tile(x, y)
                    val block = tile.block()
                    if (block == null || block.isStatic) {
                        Call.setTile(tile, Blocks.air, Team.derelict, 0)
                        Call.setFloor(tile, getFloorOfWall(block), Blocks.air)
                        Call.effect(Fx.flakExplosion, x.toFloat() * Vars.tilesize, y.toFloat() * Vars.tilesize, 0F, ArcColor.white)
                        Call.soundAt(Sounds.place, x.toFloat() * Vars.tilesize, y.toFloat() * Vars.tilesize, 1F, calcPitch(true))
                    }
                }
            }
        }
        Call.sendMessage("The excavation has finished!")
    }

    private fun renderZones() {
        for (entry in zones.entries) {
            val p1 = entry.second.p1
            if (p1 != null) {
                Call.label(
                    entry.first.con(),
                    Iconc.alphaaaa.toString(),
                    1F,
                    p1.x.toFloat() * Vars.tilesize,
                    p1.y.toFloat() * Vars.tilesize,
                )
            }
            val p2 = entry.second.p2
            if (p2 != null) {
                Call.label(
                    entry.first.con(),
                    Iconc.alphaaaa.toString(),
                    1F,
                    p2.x.toFloat() * Vars.tilesize,
                    p2.y.toFloat() * Vars.tilesize,
                )
            }
        }

        val zone = manager.current?.target ?: return
        val x1 = minOf(zone.p1!!.x, zone.p2!!.x)
        val x2 = maxOf(zone.p1.x, zone.p2.x)
        val y1 = minOf(zone.p1.y, zone.p2.y)
        val y2 = maxOf(zone.p1.y, zone.p2.y)
        for (y in y1..y2) {
            for (x in x1..x2) {
                if (x == x1 || x == x2 || y == y1 || y == y2) {
                    Call.label(
                        Iconc.alphaaaa.toString(),
                        1F,
                        x.toFloat() * Vars.tilesize,
                        y.toFloat() * Vars.tilesize,
                    )
                }
            }
        }
        val mx = x1 + ((x2 - x1) / 2)
        val my = y1 + ((y2 - y1) / 2)
        Call.label("EXCAVATION SITE", 1F, mx.toFloat() * Vars.tilesize, my.toFloat() * Vars.tilesize)
    }

    @EventHandler
    internal fun onTapEvent(event: EventType.TapEvent) {
        val zone = zones[event.player] ?: return
        val point = ImmutablePoint(event.tile.x.toInt(), event.tile.y.toInt())
        if (point.x !in 0..Vars.world.width() || point.y !in 0..Vars.world.height()) {
            event.player.sendMessage("The chosen excavate point is out of bounds, try again!")
            return
        }

        val other = if (zone.first) zone.p2 else zone.p1
        if (other != null) {
            val dx = abs(point.x - other.x)
            val dy = abs(point.y - other.y)
            if (dx >= MAX_EXCAVATE_SIZE || dy >= MAX_EXCAVATE_SIZE) {
                zones[event.player] = ExcavateZone()
                event.player.sendMessage("The chosen excavate point is too far from the other point, try again!")
                return
            }
            if (dx == 0 || dy == 0) {
                zones[event.player] = ExcavateZone()
                event.player.sendMessage("The chosen excavate point is on the same axis as the other point, try again!")
                return
            }
        }

        val adjective: String
        if (zone.first) {
            zones[event.player] = zone.copy(p1 = point, first = false)
            adjective = "first"
        } else {
            zones[event.player] = zone.copy(p2 = point, first = true)
            adjective = "second"
        }

        event.player.sendMessage("You set the $adjective point to (${point.x}, ${point.y})")
    }

    data class ExcavateZone(val p1: ImmutablePoint? = null, val p2: ImmutablePoint? = null, val first: Boolean = true)

    companion object {
        private const val MAX_EXCAVATE_SIZE = 64
    }
}

// TODO This is handy :)
private fun <C : Any> MutableCommandBuilder<C>.registerCopy(
    literal: String,
    description: ArgumentDescription = ArgumentDescription.empty(),
    aliases: Array<String> = emptyArray(),
    lambda: MutableCommandBuilder<C>.() -> Unit,
) = copy().apply { literal(literal, description, *aliases); lambda(this) }.register()

private var lastTime = System.currentTimeMillis()
private var pitchSeq: Int = 0
private fun calcPitch(up: Boolean): Float = if (System.currentTimeMillis() - lastTime < 16 * 30) {
    lastTime = System.currentTimeMillis()
    pitchSeq++
    if (pitchSeq > 30) {
        pitchSeq = 0
    }
    1f + Mathf.clamp(pitchSeq / 30f) * if (up) 1.9f else -0.4f
} else {
    pitchSeq = 0
    lastTime = System.currentTimeMillis()
    Mathf.random(0.7f, 1.3f)
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
