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
package com.xpdustry.imperium.mindustry.history

import arc.math.geom.Point2
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.misc.LimitedList
import com.xpdustry.imperium.mindustry.history.factory.CANVAS_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.factory.CommonConfigurationFactory
import com.xpdustry.imperium.mindustry.history.factory.ITEM_BRIDGE_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.factory.LIGHT_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.factory.LogicProcessorConfigurationFactory
import com.xpdustry.imperium.mindustry.history.factory.MASS_DRIVER_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.factory.MESSAGE_BLOCK_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.factory.PAYLOAD_DRIVER_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.factory.POWER_NODE_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.factory.UNIT_FACTORY_CONFIGURATION_FACTORY
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.util.Priority
import mindustry.game.EventType
import mindustry.gen.Building
import mindustry.world.Block
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.distribution.ItemBridge
import mindustry.world.blocks.distribution.MassDriver
import mindustry.world.blocks.logic.CanvasBlock
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.logic.MessageBlock
import mindustry.world.blocks.payloads.PayloadMassDriver
import mindustry.world.blocks.power.LightBlock
import mindustry.world.blocks.power.PowerNode
import mindustry.world.blocks.units.UnitFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.superclasses

class SimpleBlockHistory(private val config: ServerConfig.Mindustry) : BlockHistory, ImperiumApplication.Listener {
    private val positions = mutableMapOf<Int, LimitedList<HistoryEntry>>()
    private val players = mutableMapOf<String, LimitedList<HistoryEntry>>()
    private val factories = mutableMapOf<KClass<out Building>, HistoryConfig.Factory<*>>()

    override fun onImperiumInit() {
        setConfigurationFactory<CanvasBlock.CanvasBuild>(CANVAS_CONFIGURATION_FACTORY)
        setConfigurationFactory<Building>(CommonConfigurationFactory)
        setConfigurationFactory<ItemBridge.ItemBridgeBuild>(ITEM_BRIDGE_CONFIGURATION_FACTORY)
        setConfigurationFactory<LightBlock.LightBuild>(LIGHT_CONFIGURATION_FACTORY)
        setConfigurationFactory<LogicBlock.LogicBuild>(LogicProcessorConfigurationFactory)
        setConfigurationFactory<MassDriver.MassDriverBuild>(MASS_DRIVER_CONFIGURATION_FACTORY)
        setConfigurationFactory<MessageBlock.MessageBuild>(MESSAGE_BLOCK_CONFIGURATION_FACTORY)
        setConfigurationFactory<PayloadMassDriver.PayloadDriverBuild>(PAYLOAD_DRIVER_CONFIGURATION_FACTORY)
        setConfigurationFactory<PowerNode.PowerNodeBuild>(POWER_NODE_CONFIGURATION_FACTORY)
        setConfigurationFactory<UnitFactory.UnitFactoryBuild>(UNIT_FACTORY_CONFIGURATION_FACTORY)
    }

    private inline fun <reified B : Building> setConfigurationFactory(factory: HistoryConfig.Factory<B>) {
        factories[B::class] = factory
    }

    override fun getHistory(x: Int, y: Int): List<HistoryEntry> {
        return positions[Point2.pack(x, y)] ?: emptyList()
    }

    override fun getHistory(uuid: String): List<HistoryEntry> {
        return players[uuid] ?: emptyList()
    }

    @EventHandler(priority = Priority.HIGH)
    fun onBlockBuildEndEvent(event: EventType.BlockBuildEndEvent) {
        if (event.unit == null || event.tile.build == null) {
            return
        }

        // TODO Check if tile is ConstructBlock ?
        val block: Block = if (event.breaking) (event.tile.build as ConstructBlock.ConstructBuild).current else event.tile.block()
        this.addEntry(event.tile.build, block, event.unit, if (event.breaking) HistoryEntry.Type.BREAK else HistoryEntry.Type.PLACE, event.config)
    }

    @EventHandler(priority = Priority.HIGH)
    fun onBlockDestroyBeginEvent(event: EventType.BlockBuildBeginEvent) {
        if (event.unit == null) {
            return
        }
        val build = event.tile.build
        if (build is ConstructBlock.ConstructBuild) {
            this.addEntry(
                build,
                build.current,
                event.unit,
                if (event.breaking) HistoryEntry.Type.BREAKING else HistoryEntry.Type.PLACING,
                build.lastConfig,
            )
        }
    }

    @EventHandler(priority = Priority.HIGH)
    fun onBLockConfigEvent(event: EventType.ConfigEvent) {
        if (event.player == null) {
            return
        }
        this.addEntry(event.tile, event.tile.block(), event.player.unit(), HistoryEntry.Type.CONFIGURE, event.value)
    }

    @EventHandler(priority = Priority.HIGH)
    fun onWorldLoadEvent(event: EventType.WorldLoadEvent) {
        positions.clear()
        players.clear()
    }

    @EventHandler(priority = Priority.HIGH)
    fun onBlockRotateEvent(event: EventType.BuildRotateEvent) {
        if (event.unit == null || event.build.rotation == event.previous) {
            return
        }
        this.addEntry(event.build, event.build.block(), event.unit, HistoryEntry.Type.ROTATE, event.build.config())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <B : Building> getConfiguration(
        building: B,
        type: HistoryEntry.Type,
        config: Any?,
    ): HistoryConfig? {
        if (building.block().configurations.isEmpty) {
            return null
        }
        var clazz: KClass<*> = building::class
        while (Building::class.isSuperclassOf(clazz)) {
            val factory: HistoryConfig.Factory<B>? = factories[clazz] as HistoryConfig.Factory<B>?
            if (factory != null) {
                return factory.create(building, type, config)
            }
            clazz = clazz.superclasses.first()
        }
        return if (config == null) HistoryConfig.Simple(null) else HistoryConfig.Simple(config)
    }

    private fun addEntry(
        building: Building,
        block: Block,
        unit: mindustry.gen.Unit,
        type: HistoryEntry.Type,
        config: Any?,
    ) {
        val configuration = getConfiguration(building, type, config)
        val author = HistoryAuthor(unit)
        building.tile.getLinkedTiles {
            addEntry(
                HistoryEntry(
                    it.x.toInt(),
                    it.y.toInt(),
                    building.tileX(),
                    building.tileY(),
                    author,
                    block,
                    type,
                    building.rotation,
                    configuration,
                    it.pos() != building.tile.pos(),
                ),
            )
        }
    }

    private fun addEntry(entry: HistoryEntry) {
        val entries = positions.computeIfAbsent(Point2.pack(entry.x, entry.y)) {
            LimitedList(config.history.tileEntriesLimit)
        }
        val previous: HistoryEntry? = entries.peekLast()
        // Some blocks have repeating configurations, we don't want to spam the history with them
        if (previous != null && haveSameConfiguration(previous, entry)) {
            entries.removeLast()
        }
        entries.add(entry)
        if (entry.author.uuid != null && !entry.virtual) {
            players
                .computeIfAbsent(entry.author.uuid) { LimitedList(config.history.playerEntriesLimit) }
                .add(entry)
        }
    }

    private fun haveSameConfiguration(entryA: HistoryEntry, entryB: HistoryEntry): Boolean {
        return entryA.block == entryB.block && entryA.configuration == entryB.configuration && entryA.type === entryB.type
    }
}
