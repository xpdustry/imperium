/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
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
package com.xpdustry.imperium.mindustry.tower

import com.xpdustry.distributor.annotation.method.EventHandler
import com.xpdustry.distributor.annotation.method.TaskHandler
import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.distributor.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.collection.MutableUnionSet
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.snowflake
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.sqrt
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Iconc
import mindustry.gen.Player

class TowerEditorListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val editors = PlayerMap<EditMode>(instances.get())
    private val directory = instances.get<Path>("directory").resolve("tower")
    private val manager = WaypointManager()

    @ImperiumCommand(["tower", "editor", "mark"], rank = Rank.ADMIN)
    @Scope(MindustryGamemode.TOWER_DEFENSE)
    @ClientSide
    fun onTowerEditorMark(sender: CommandSender) {
        when (editors[sender.player]) {
            is EditMode.Mark -> {
                editors.remove(sender.player)
                sender.sendMessage("You are no longer marking tiles as waypoints.")
            }
            else -> {
                editors[sender.player] = EditMode.Mark
                sender.sendMessage("You are now marking tiles as waypoints.")
            }
        }
    }

    @ImperiumCommand(["tower", "editor", "link"], rank = Rank.ADMIN)
    @Scope(MindustryGamemode.TOWER_DEFENSE)
    @ClientSide
    fun onTowerEditorLink(sender: CommandSender) {
        when (editors[sender.player]) {
            is EditMode.Link -> {
                editors.remove(sender.player)
                sender.sendMessage("You are no longer linking waypoints.")
            }
            else -> {
                editors[sender.player] = EditMode.Link()
                sender.sendMessage("You are now linking waypoints.")
            }
        }
    }

    @EventHandler
    fun onMenuToPlay(event: MenuToPlayEvent) {
        manager.clear()
    }

    @EventHandler
    fun onTapEvent(event: EventType.TapEvent) {
        val edit = editors[event.player] ?: return
        val point = ImmutablePoint(event.tile.x.toInt(), event.tile.y.toInt())
        when (edit) {
            is EditMode.Mark -> {
                if (manager.add(point)) {
                    event.player.sendMessage("Added waypoint at $point.")
                } else {
                    manager.remove(point)
                    event.player.sendMessage("Removed waypoint at $point.")
                }
            }
            is EditMode.Link -> {
                if (point !in manager.waypoints) {
                    event.player.sendMessage("The selected point is not a waypoint.")
                } else if (edit.origin == null) {
                    editors[event.player] = EditMode.Link(point)
                    event.player.sendMessage("Selected the origin at $point.")
                } else {
                    if (manager.setLink(edit.origin, point)) {
                        event.player.sendMessage("Linked ${edit.origin} to $point.")
                    } else if (manager.removeLink(edit.origin, point)) {
                        event.player.sendMessage("Unlinked ${edit.origin} from $point.")
                    } else {
                        event.player.sendMessage("Unable to link ${edit.origin} to $point.")
                    }
                    editors[event.player] = EditMode.Link()
                }
            }
        }
    }

    /*
    @OptIn(ExperimentalSerializationApi::class)
    private fun loadTowerData(): TowerData {
        val waypoints =
            directory.resolve("${getCurrentMapName()}.json").inputStream().use {
                Json.decodeFromStream<Map<Int, List<Int>>>(it)
            }
        return TowerData(
            waypoints.entries.associateTo(mutableMapOf()) { (point, next) ->
                Point2.unpack(point).toImmutablePoint() to
                    next.asSequence().map(Point2::unpack).map(Point2::toImmutablePoint).toMutableSet()
            })
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun saveTowerData(data: TowerData) {
        val waypoints = data.waypoints.mapKeys { (point, _) -> Point2.pack(point.x, point.y) }
            .mapValues { (_, next) -> next.map { Point2.pack(it.x, it.y) } }
        directory.resolve("${getCurrentMapName()}.json").outputStream().use {
            Json.encodeToStream(waypoints, it)
        }
    }

     */

    @TaskHandler(interval = 1L, unit = MindustryTimeUnit.SECONDS)
    fun onTowerEditorUpdate() {
        for (player in editors.entries.map(Pair<Player, EditMode>::first)) {
            for (waypoint in manager.waypoints) {
                Call.label(
                    player.con(),
                    "[accent]W",
                    1F,
                    waypoint.x * Vars.tilesize.toFloat(),
                    waypoint.y * Vars.tilesize.toFloat())
                for (link in manager.getLinks(waypoint)) {
                    val dx = (link.x - waypoint.x).toDouble()
                    val dy = (link.y - waypoint.y).toDouble()
                    val angle = (atan2(dy, dx) * (180 / PI)).toInt()
                    val icon =
                        when (angle) {
                            in 45..135 -> Iconc.up
                            in 135..225 -> Iconc.left
                            in 225..315 -> Iconc.down
                            else -> Iconc.right
                        }
                    val length = ceil(sqrt(dx * dx + dy * dy)).toInt()
                    repeat(length) { i ->
                        val x = waypoint.x + dx.toInt() * (i + 0.5F) / length
                        val y = waypoint.y + dy.toInt() * (i + 0.5F) / length
                        Call.label(
                            player.con(), "[accent]$icon", 1F, x * Vars.tilesize, y * Vars.tilesize)
                    }
                }
            }
        }
    }

    private fun getCurrentMapName() =
        Vars.state.map.snowflake ?: Vars.state.map.name() ?: error("The current map has no name.")

    private class WaypointManager {
        private val union = MutableUnionSet<ImmutablePoint>()
        private val links = mutableMapOf<ImmutablePoint, MutableSet<ImmutablePoint>>()
        val waypoints: Set<ImmutablePoint>
            get() = union.elements

        fun add(point: ImmutablePoint) = union.addElement(point)

        fun remove(point: ImmutablePoint) {
            union.removeElement(point)
            links.remove(point)
            links.values.forEach { it -= point }
        }

        fun getLinks(point: ImmutablePoint) = links[point] ?: emptySet()

        fun setLink(point1: ImmutablePoint, point2: ImmutablePoint): Boolean {
            if (point1 !in union.elements || point2 !in union.elements) return false
            if (union.getUnion(point1).contains(point2)) return false
            union.setUnion(point1, point2)
            links.getOrPut(point1) { mutableSetOf() } += point2
            return true
        }

        fun removeLink(point1: ImmutablePoint, point2: ImmutablePoint): Boolean {
            links[point1]?.remove(point2)
            return union.removeUnion(point1, point2)
        }

        fun clear() {
            union.clear()
            links.clear()
        }
    }

    private sealed interface EditMode {
        data object Mark : EditMode

        data class Link(val origin: ImmutablePoint? = null) : EditMode
    }
}
