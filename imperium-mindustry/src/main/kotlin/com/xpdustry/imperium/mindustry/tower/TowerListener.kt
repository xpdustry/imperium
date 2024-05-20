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

import arc.func.Func
import arc.graphics.Color
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.misc.asList
import mindustry.Vars
import mindustry.content.Fx
import mindustry.content.StatusEffects
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.net.Administration
import mindustry.type.UnitType
import mindustry.type.unit.MissileUnitType
import mindustry.world.blocks.units.Reconstructor

class TowerListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val plugin = instances.get<MindustryPlugin>()
    private val downgrades = mutableMapOf<UnitType, UnitType>()

    override fun onImperiumInit() {
        Vars.pathfinder = TowerPathfinder(plugin)

        // Can't control enemy units
        Vars.netServer.admins.addActionFilter {
            !(it.type == Administration.ActionType.control &&
                it.unit.team() == Vars.state.rules.waveTeam)
        }

        Vars.content
            .units()
            .asList()
            .filter { it !is MissileUnitType }
            .forEach { type ->
                val previous = type.controller
                type.controller = Func { unit ->
                    if (unit.team() == Vars.state.rules.waveTeam) GroundTowerAI()
                    else previous.get(unit)
                }
            }

        Vars.content.blocks().asList().filterIsInstance<Reconstructor>().forEach { block ->
            block.upgrades.forEach { upgrade ->
                if (upgrade[1] in downgrades) {
                    LOGGER.warn(
                        "Duplicate downgrade for {}, got {} and {}",
                        upgrade[1].name,
                        downgrades[upgrade[1]]!!.name,
                        upgrade[0].name)
                } else {
                    downgrades[upgrade[1]] = upgrade[0]
                }
            }
        }
    }

    @EventHandler
    fun onUnitDestroyEvent(event: EventType.UnitDestroyEvent) {
        if (event.unit.team() == Vars.state.rules.waveTeam) {
            val downgrade = downgrades[event.unit.type] ?: return
            val unit = downgrade.create(Vars.state.rules.waveTeam)
            unit.set(event.unit.x, event.unit.y)
            unit.apply(
                StatusEffects.invincible,
                MindustryTimeUnit.TICKS.convert(5L, MindustryTimeUnit.SECONDS).toFloat())
            unit.apply(
                StatusEffects.slow,
                MindustryTimeUnit.TICKS.convert(5L, MindustryTimeUnit.SECONDS).toFloat())
            unit.add()
            Call.effect(Fx.spawn, event.unit.x, event.unit.y, 0f, Color.red)
        }
    }

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}
