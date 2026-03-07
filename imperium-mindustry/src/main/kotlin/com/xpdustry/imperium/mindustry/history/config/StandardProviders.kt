// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.history.config

import arc.math.geom.Point2
import com.xpdustry.imperium.mindustry.history.HistoryEntry
import java.nio.ByteBuffer
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
    BlockConfig.Provider<CanvasBuild> { _, _, config ->
        if (config is ByteArray) BlockConfig.Canvas(ByteBuffer.wrap(config.clone())) else null
    }

val LIGHT_CONFIGURATION_FACTORY =
    BlockConfig.Provider<LightBlock.LightBuild> { _, _, config ->
        if (config is Int) BlockConfig.Light(config) else null
    }

val MESSAGE_BLOCK_CONFIGURATION_FACTORY =
    BlockConfig.Provider<MessageBlock.MessageBuild> { _, _, config ->
        if (config is String) BlockConfig.Text(config) else null
    }

val UNIT_FACTORY_CONFIGURATION_FACTORY =
    object : BlockConfig.Provider<UnitFactory.UnitFactoryBuild> {
        override fun create(
            building: UnitFactory.UnitFactoryBuild,
            type: HistoryEntry.Type,
            config: Any?,
        ): BlockConfig? {
            val plans = (building.block as UnitFactory).plans
            return if (config is Int) {
                if (config > 0 && config < plans.size) BlockConfig.Content(plans[config].unit) else BlockConfig.Reset
            } else if (config is UnitType) {
                create(building, type, plans.indexOf { plan -> plan.unit == config })
            } else {
                null
            }
        }
    }

val ITEM_BRIDGE_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigProvider<ItemBridge.ItemBridgeBuild>() {
        override fun isLinkValid(building: ItemBridge.ItemBridgeBuild, x: Int, y: Int) =
            (building.block as ItemBridge).linkValid(building.tile, Vars.world.tile(x, y))
    }

val MASS_DRIVER_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigProvider<MassDriver.MassDriverBuild>() {
        override fun isLinkValid(building: MassDriver.MassDriverBuild, x: Int, y: Int): Boolean {
            if (Point2.pack(x, y) == -1) return false
            val other = Vars.world.build(Point2.pack(x, y))
            return other is MassDriver.MassDriverBuild &&
                building.block === other.block &&
                building.team === other.team &&
                building.within(other, (building.block as MassDriver).range)
        }
    }

val POWER_NODE_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigProvider<PowerNode.PowerNodeBuild>() {
        override fun isLinkValid(building: PowerNode.PowerNodeBuild, x: Int, y: Int) =
            building.power.links.contains(Point2.pack(x, y))
    }

val PAYLOAD_DRIVER_CONFIGURATION_FACTORY =
    object : LinkableBlockConfigProvider<PayloadMassDriver.PayloadDriverBuild>() {
        override fun isLinkValid(building: PayloadMassDriver.PayloadDriverBuild, x: Int, y: Int): Boolean {
            val other = Vars.world.build(Point2.pack(x, y))
            return other is MassDriver.MassDriverBuild &&
                building.block === other.block &&
                building.team === other.team &&
                building.within(other, (building.block as PayloadMassDriver).range)
        }
    }
