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

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.util.Priority
import java.util.Collections
import java.util.IdentityHashMap
import mindustry.Vars
import mindustry.ai.Pathfinder
import mindustry.content.Blocks
import mindustry.game.EventType.WorldLoadEvent
import mindustry.net.Administration
import mindustry.world.Tile
import mindustry.world.blocks.environment.Floor

class TowerPathfinder(plugin: MindustryPlugin) : Pathfinder() {

    private val towerPassableFloors = Collections.newSetFromMap<Floor>(IdentityHashMap())

    init {
        wrapPathCost(costGround)
        wrapPathCost(costLegs)

        Distributor.get().eventBus.subscribe(WorldLoadEvent::class.java, Priority.HIGH, plugin) {
            for (tile in Vars.world.tiles) {
                if (tile.overlay() === Blocks.spawn) {
                    towerPassableFloors.add(tile.floor())
                }
            }
        }

        Vars.netServer.admins.addActionFilter {
            !(it.type == Administration.ActionType.placeBlock && it.tile.floor() in towerPassableFloors)
        }
    }

    override fun packTile(tile: Tile): Int {
        val towerPassable = (if (tile.floor() in towerPassableFloors) BIT_MASK_TOWER_PASSABLE else 0)
        return super.packTile(tile) or towerPassable
    }

    private fun wrapPathCost(pathCostType: Int) {
        val costType = costTypes.get(pathCostType)
        costTypes.set(pathCostType) { team, tile ->
            val towerPassable = tile and BIT_MASK_TOWER_PASSABLE != 0
            var cost = costType.getCost(team, tile)
            if (!towerPassable && team == Vars.state.rules.waveTeam.id && cost >= 0) {
                cost += 6000
            }
            return@set cost
        }
    }

    companion object {
        private const val BIT_MASK_TOWER_PASSABLE = (1 shl 30)
    }
}
