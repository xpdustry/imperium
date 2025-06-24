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
package com.xpdustry.imperium.mindustry.history

import arc.math.geom.Point2
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.component.render.ComponentStringBuilder
import com.xpdustry.distributor.api.key.KeyContainer
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.collection.LimitedList
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.history.HistoryRequestMessage
import com.xpdustry.imperium.common.history.HistoryResponseMessage
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.function
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.history.config.BaseBlockConfigProvider
import com.xpdustry.imperium.mindustry.history.config.BlockConfig
import com.xpdustry.imperium.mindustry.history.config.CANVAS_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.config.ITEM_BRIDGE_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.config.LIGHT_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.config.LogicProcessorConfigProvider
import com.xpdustry.imperium.mindustry.history.config.MASS_DRIVER_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.config.MESSAGE_BLOCK_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.config.PAYLOAD_DRIVER_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.config.POWER_NODE_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.history.config.UNIT_FACTORY_CONFIGURATION_FACTORY
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.superclasses
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Nulls
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

interface Historian {
    fun getHistory(x: Int, y: Int): List<HistoryEntry>

    fun getHistory(uuid: MindustryUUID): List<HistoryEntry>
}

fun List<HistoryEntry>.normalize(limit: Int) =
    asReversed()
        .asSequence()
        .withIndex()
        .filter {
            it.index == 0 || (it.value.type != HistoryEntry.Type.BREAKING && it.value.type != HistoryEntry.Type.PLACING)
        }
        .map { it.value }
        .take(limit)
        .toList()

class SimpleHistorian(
    private val imperium: ImperiumConfig,
    private val config: ImperiumConfig,
    private val users: UserManager,
    private val renderer: HistoryRenderer,
    private val messenger: Messenger,
) : Historian, ImperiumApplication.Listener {
    private val positions = mutableMapOf<Int, LimitedList<HistoryEntry>>()
    private val players = mutableMapOf<String, LimitedList<HistoryEntry>>()
    private val providers = mutableMapOf<KClass<out Building>, BlockConfig.Provider<*>>()

    override fun onImperiumInit() {
        setProvider<CanvasBlock.CanvasBuild>(CANVAS_CONFIGURATION_FACTORY)
        setProvider<Building>(BaseBlockConfigProvider)
        setProvider<ItemBridge.ItemBridgeBuild>(ITEM_BRIDGE_CONFIGURATION_FACTORY)
        setProvider<LightBlock.LightBuild>(LIGHT_CONFIGURATION_FACTORY)
        setProvider<LogicBlock.LogicBuild>(LogicProcessorConfigProvider)
        setProvider<MassDriver.MassDriverBuild>(MASS_DRIVER_CONFIGURATION_FACTORY)
        setProvider<MessageBlock.MessageBuild>(MESSAGE_BLOCK_CONFIGURATION_FACTORY)
        setProvider<PayloadMassDriver.PayloadDriverBuild>(PAYLOAD_DRIVER_CONFIGURATION_FACTORY)
        setProvider<PowerNode.PowerNodeBuild>(POWER_NODE_CONFIGURATION_FACTORY)
        setProvider<UnitFactory.UnitFactoryBuild>(UNIT_FACTORY_CONFIGURATION_FACTORY)

        messenger.function<HistoryRequestMessage, HistoryResponseMessage> { request ->
            if (!request.server.equals(imperium.server.name, ignoreCase = true)) return@function null
            val user = users.findById(request.player) ?: return@function null
            val (team, unit) =
                runMindustryThread {
                    val player = Entities.getPlayers().firstOrNull { it.uuid() == user.uuid }
                    (player?.team() ?: Team.sharded) to (player?.unit()?.type ?: Nulls.unit.type)
                }
            HistoryResponseMessage(
                ComponentStringBuilder.plain(KeyContainer.empty())
                    .append(renderer.render(getHistory(user.uuid).normalize(30), HistoryActor(user.uuid, team, unit)))
                    .toString()
            )
        }
    }

    private inline fun <reified B : Building> setProvider(provider: BlockConfig.Provider<B>) {
        providers[B::class] = provider
    }

    override fun getHistory(x: Int, y: Int): List<HistoryEntry> {
        return positions[Point2.pack(x, y)]?.toList() ?: emptyList()
    }

    override fun getHistory(uuid: String): List<HistoryEntry> {
        return players[uuid]?.toList() ?: emptyList()
    }

    @EventHandler(priority = Priority.HIGH)
    fun onBlockBuildEndEvent(event: EventType.BlockBuildEndEvent) {
        if (event.unit == null || event.tile.build == null) {
            return
        }

        val block: Block =
            if (event.breaking) (event.tile.build as ConstructBlock.ConstructBuild).current else event.tile.block()
        this.addEntry(
            event.tile.build,
            block,
            event.unit,
            if (event.breaking) HistoryEntry.Type.BREAK else HistoryEntry.Type.PLACE,
            event.config,
        )
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
    fun onMenuToPlayEvent(event: MenuToPlayEvent) {
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
    private fun <B : Building> getConfiguration(building: B, type: HistoryEntry.Type, config: Any?): BlockConfig? {
        if (building.block().configurations.isEmpty) {
            return null
        }
        var clazz: KClass<*> = building::class
        while (Building::class.isSuperclassOf(clazz)) {
            val provider: BlockConfig.Provider<B>? = providers[clazz] as BlockConfig.Provider<B>?
            if (provider != null) {
                return provider.create(building, type, config)
            }
            clazz = clazz.superclasses.first()
        }
        return null
    }

    private fun addEntry(
        building: Building,
        block: Block,
        unit: mindustry.gen.Unit,
        type: HistoryEntry.Type,
        config: Any?,
    ) {
        val configuration = getConfiguration(building, type, config)
        val author = HistoryActor(unit)
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
                )
            )
        }
    }

    private fun addEntry(entry: HistoryEntry) {
        val entries =
            positions.computeIfAbsent(Point2.pack(entry.x, entry.y)) {
                LimitedList(config.mindustry.history.tileEntriesLimit)
            }
        val previous: HistoryEntry? = entries.peekLast()
        // Some blocks have repeating configurations, we don't want to spam the history with them
        if (previous != null && haveSameConfiguration(previous, entry)) {
            entries.removeLast()
        }
        entries.add(entry)
        if (entry.actor.player != null && !entry.virtual) {
            players
                .computeIfAbsent(entry.actor.player) { LimitedList(config.mindustry.history.playerEntriesLimit) }
                .add(entry)
        }
    }

    private fun haveSameConfiguration(entryA: HistoryEntry, entryB: HistoryEntry) =
        entryA.block == entryB.block && entryA.config == entryB.config && entryA.type === entryB.type
}
