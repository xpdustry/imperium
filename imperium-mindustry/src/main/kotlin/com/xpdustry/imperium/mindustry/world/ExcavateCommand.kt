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
package com.xpdustry.imperium.mindustry.world

import arc.graphics.Color
import arc.math.Mathf
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.event.EventHandler
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Iconc
import mindustry.gen.Sounds
import mindustry.type.Item

class ExcavateCommand(instances: InstanceManager) :
    AbstractVoteCommand<ExcavateCommand.ExcavateData>(instances.get(), "excavate", 1.minutes),
    ImperiumApplication.Listener {

    private val areas = PlayerMap<ExcavateArea>(instances.get())
    private val config = instances.get<ServerConfig.Mindustry>()
    private lateinit var item: Item

    override fun onImperiumInit() {
        item =
            Vars.content.item(config.world.excavationItem)
                ?: error("${config.world.excavationItem} is not a valid mindustry item.")
        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(1.seconds)
                if (Vars.state.isPlaying) {
                    renderZones()
                }
            }
        }
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
                event.player.sendMessage(
                    "The chosen excavate point is too far from the other point, try again!")
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

    @Command(["excavate|e", "select|s"])
    @Scope(MindustryGamemode.SURVIVAL, MindustryGamemode.ATTACK, MindustryGamemode.SURVIVAL_EXPERT)
    @ClientSide
    private fun onExcavateSelectCommand(sender: CommandSender) {
        if (areas[sender.player] != null) {
            areas.remove(sender.player)
            sender.sendMessage("You have cancelled selecting excavation points!")
        } else {
            areas[sender.player] = ExcavateArea()
            sender.sendMessage("You have started selecting excavation points!.")
        }
    }

    @Command(["excavate|e", "y"])
    @Scope(MindustryGamemode.SURVIVAL, MindustryGamemode.ATTACK, MindustryGamemode.SURVIVAL_EXPERT)
    @ClientSide
    private fun onExcavateYesCommand(sender: CommandSender) {
        val area = areas[sender.player]
        if (area == null) {
            onPlayerVote(sender.player, manager.session, Vote.YES)
            return
        }
        if (area.p1 == UNSET_POINT || area.p2 == UNSET_POINT) {
            sender.sendMessage("You have not selected both points yet!")
            return
        }

        var price = 0
        for (x in area.x1..area.x2) {
            for (y in area.y1..area.y2) {
                if (Vars.world.tile(x, y).block()?.isStatic == true) {
                    price += config.world.excavationTilePrice
                }
            }
        }

        if (price == 0) {
            sender.sendWarning("[scarlet]The area you selected does no contain any walls.")
            return
        }

        val items = Vars.state.rules.defaultTeam.items()
        if (items.has(item, price)) {
            Vars.state.rules.defaultTeam.items().remove(item, price)
        } else {
            sender.sendMessage(
                "[scarlet]You do not have enough ${item.name} to do that. [orange]${price - items.get(item)}[] more ${item.name} is needed; You have ${item.get(item)} ${item.name}.")
            return
        }

        onVoteSessionStart(sender.player, manager.session, ExcavateData(price, area))
        areas.remove(sender.player)
    }

    @Command(["excavate|e", "n"])
    @Scope(MindustryGamemode.SURVIVAL, MindustryGamemode.ATTACK, MindustryGamemode.SURVIVAL_EXPERT)
    @ClientSide
    private fun onExcavateNoCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.NO)
    }

    @Command(["excavate|e", "cancel|c"], Rank.MODERATOR)
    @Scope(MindustryGamemode.SURVIVAL, MindustryGamemode.ATTACK, MindustryGamemode.SURVIVAL_EXPERT)
    @ClientSide
    private fun onExcavateCancelCommand(sender: CommandSender) {
        onPlayerCancel(sender.player, manager.session)
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<ExcavateData>): String {
        val area = session.objective.area
        return "Type [accent]/e y[] to remove the walls in-between [red](${area.x1}, ${area.y1})[] and[red] (${area.x2}, ${area.y2})."
    }

    override fun getRequiredVotes(players: Int): Int =
        when (Entities.getPlayers().size) {
            0 -> 0
            1 -> 1
            2,
            3,
            4,
            5 -> 2
            6,
            7,
            8,
            9 -> 3
            else -> 4
        }

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<ExcavateData>) {
        val sequence = AtomicInteger(0)
        val area = session.objective.area
        for (y in area.y1..area.y2) {
            delay(100.milliseconds)
            runMindustryThread {
                for (x in area.x1..area.x2) {
                    val tile = Vars.world.tile(x, y)
                    if (tile.block()?.isStatic != true) {
                        continue
                    }
                    val floor = tile.floor()
                    Call.setTile(tile, Blocks.air, Team.derelict, 0)
                    Call.setFloor(tile, floor, Blocks.air)
                    Call.effect(
                        Fx.flakExplosion,
                        x.toFloat() * Vars.tilesize,
                        y.toFloat() * Vars.tilesize,
                        0F,
                        Color.white)
                }
                val cx = area.x1 + ((area.x2 - area.x1) / 2F) * Vars.tilesize
                Call.soundAt(
                    Sounds.place, cx, y.toFloat() * Vars.tilesize, 1F, getNextPitch(sequence))
            }
        }
        Call.sendMessage("The excavation has finished!")
    }

    override suspend fun onVoteSessionFailure(session: VoteManager.Session<ExcavateData>) {
        Vars.state.rules.defaultTeam.items().add(item, session.objective.price)
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

        val area = manager.session?.objective?.area ?: return
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
        Call.label(
            "EXCAVATION SITE",
            1F,
            mx.toFloat() * Vars.tilesize + half,
            my.toFloat() * Vars.tilesize + half)
    }

    private fun getNextPitch(sequence: AtomicInteger): Float {
        if (sequence.incrementAndGet() > 30) {
            sequence.set(0)
        }
        // Anuke black magic
        return 1f + Mathf.clamp(sequence.get() / 30f) * 1.9f
    }

    data class ExcavateData(val price: Int, val area: ExcavateArea)

    data class ExcavateArea(
        val p1: ImmutablePoint = UNSET_POINT,
        val p2: ImmutablePoint = UNSET_POINT,
        val first: Boolean = true
    ) {
        val x1 = min(p1.x, p2.x)
        val x2 = max(p1.x, p2.x)
        val y1 = min(p1.y, p2.y)
        val y2 = max(p1.y, p2.y)
    }

    companion object {
        private val UNSET_POINT = ImmutablePoint(-1, -1)
    }
}
