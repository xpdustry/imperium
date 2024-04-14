/*
 * Imperium, the software collection powering the Xpdustry network.
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
 *
 */
package com.xpdustry.imperium.mindustry.history.factory

import arc.math.geom.Point2
import com.xpdustry.imperium.mindustry.history.HistoryConfig
import com.xpdustry.imperium.mindustry.history.HistoryEntry
import java.awt.Color
import mindustry.Vars
import mindustry.type.UnitType
import mindustry.world.blocks.distribution.ItemBridge
import mindustry.world.blocks.distribution.MassDriver
import mindustry.world.blocks.logic.CanvasBlock.CanvasBuild
import mindustry.world.blocks.logic.MessageBlock
import mindustry.world.blocks.payloads.PayloadMassDriver
import mindustry.world.blocks.power.LightBlock
import mindustry.world.blocks.power.PowerNode
import mindustry.world.blocks.units.UnitFactory

val CANVAS_CONFIGURATION_FACTORY =
    HistoryConfig.Factory<CanvasBuild> { _, _, config ->
        if (config is ByteArray) HistoryConfig.Canvas(config) else null
    }

val LIGHT_CONFIGURATION_FACTORY =
    HistoryConfig.Factory<LightBlock.LightBuild> { _, _, config ->
        if (config is Int) HistoryConfig.Light(Color(config, true)) else null
    }

val MESSAGE_BLOCK_CONFIGURATION_FACTORY =
    HistoryConfig.Factory<MessageBlock.MessageBuild> { _, _, config ->
        if (config is String) HistoryConfig.Text(config, HistoryConfig.Text.Type.MESSAGE) else null
    }

val UNIT_FACTORY_CONFIGURATION_FACTORY =
    object : HistoryConfig.Factory<UnitFactory.UnitFactoryBuild> {
        override fun create(
            building: UnitFactory.UnitFactoryBuild,
            type: HistoryEntry.Type,
            config: Any?
        ): HistoryConfig? {
            val plans = (building.block as UnitFactory).plans
            if (config is Int) {
                return if (config > 0 && config < plans.size)
                    HistoryConfig.Content(plans[config].unit)
                else HistoryConfig.Content(null)
            } else if (config is UnitType) {
                return create(building, type, plans.indexOf { plan -> plan.unit == config })
            }
            return null
        }
    }

val ITEM_BRIDGE_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigurationFactory<ItemBridge.ItemBridgeBuild>() {
        override fun isLinkValid(building: ItemBridge.ItemBridgeBuild, x: Int, y: Int): Boolean {
            return (building.block() as ItemBridge).linkValid(
                building.tile(), Vars.world.tile(x, y))
        }
    }

val MASS_DRIVER_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigurationFactory<MassDriver.MassDriverBuild>() {
        override fun isLinkValid(building: MassDriver.MassDriverBuild, x: Int, y: Int): Boolean {
            if (Point2.pack(x, y) == -1) {
                return false
            }
            val other = Vars.world.build(Point2.pack(x, y))
            return other is MassDriver.MassDriverBuild &&
                building.block === other.block &&
                building.team === other.team &&
                building.within(other, (building.block as MassDriver).range)
        }
    }

val POWER_NODE_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigurationFactory<PowerNode.PowerNodeBuild>() {
        override fun isLinkValid(building: PowerNode.PowerNodeBuild, x: Int, y: Int): Boolean {
            return building.power().links.contains(Point2.pack(x, y))
        }
    }

val PAYLOAD_DRIVER_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigurationFactory<PayloadMassDriver.PayloadDriverBuild>() {
        override fun isLinkValid(
            building: PayloadMassDriver.PayloadDriverBuild,
            x: Int,
            y: Int
        ): Boolean {
            val other = Vars.world.build(Point2.pack(x, y))
            return other is MassDriver.MassDriverBuild &&
                building.block === other.block &&
                building.team === other.team &&
                building.within(other, (building.block as PayloadMassDriver).range)
        }
    }
