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
package com.xpdustry.imperium.mindustry.game

import arc.math.geom.Point2
import arc.struct.IntSet
import com.xpdustry.distributor.api.DistributorProvider
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TriggerHandler
import com.xpdustry.distributor.api.collection.MindustryCollections
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.MindustryConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.mindustry.translation.announcement_dangerous_block_build
import com.xpdustry.imperium.mindustry.translation.announcement_impending_explosion_alert
import com.xpdustry.imperium.mindustry.translation.announcement_power_void_destroyed
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Building
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.ConstructBlock.ConstructBuild
import mindustry.world.blocks.power.ConsumeGenerator
import mindustry.world.blocks.power.ConsumeGenerator.ConsumeGeneratorBuild
import mindustry.world.blocks.power.NuclearReactor
import mindustry.world.blocks.production.Incinerator
import mindustry.world.blocks.sandbox.PowerVoid
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.storage.StorageBlock
import mindustry.world.consumers.ConsumeItemExplode

class AlertListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val explosives =
        MindustryCollections.immutableList(Vars.content.items()).filter { it.explosiveness > 0 }
    private val generators = IntSet()
    private val generatorsRateLimiter =
        SimpleRateLimiter<Int>(1, instances.get<MindustryConfig>().world.explosiveDamageAlertDelay)

    @EventHandler
    fun onExplosiveGeneratorPreChange(event: EventType.TilePreChangeEvent) {
        if (event.tile.block() is ConsumeGenerator) generators.remove(event.tile.pos())
    }

    @EventHandler
    fun onExplosiveGeneratorChange(event: EventType.TileChangeEvent) {
        if (event.tile.block() is ConsumeGenerator) generators.add(event.tile.pos())
    }

    @TriggerHandler(EventType.Trigger.update)
    fun onExplosiveGeneratorCheck() {
        if ((!Vars.state.rules.reactorExplosions ||
            (Vars.state.rules.infiniteResources && !Vars.state.rules.damageExplosions)))
            return
        val iterator = generators.iterator()
        while (iterator.hasNext) {
            val pos = iterator.next()
            val x = Point2.x(pos).toInt()
            val y = Point2.y(pos).toInt()
            val building = Vars.world.tile(x, y).build as? ConsumeGeneratorBuild ?: continue
            val block = building.block() as ConsumeGenerator
            val consumers = block.consumers.filterIsInstance<ConsumeItemExplode>()
            for (item in explosives) {
                if (building.items.has(item) &&
                    consumers.any { item.explosiveness > it.threshold } &&
                    generatorsRateLimiter.incrementAndCheck(pos)) {
                    DistributorProvider.get()
                        .audienceProvider
                        .players
                        .sendMessage(announcement_impending_explosion_alert(block, x, y))
                    break
                }
            }
        }
    }

    @EventHandler
    fun onPowerVoidDestroy(event: EventType.BlockDestroyEvent) {
        if (event.tile.block() is PowerVoid && !Vars.state.rules.infiniteResources) {
            notifyPowerVoidDestroyed(event.tile.x.toInt(), event.tile.y.toInt())
        }
    }

    @EventHandler
    fun onPowerVoidDelete(event: EventType.BlockBuildBeginEvent) {
        val building = event.tile.build
        if (event.breaking &&
            building is ConstructBuild &&
            building.current is PowerVoid &&
            !Vars.state.rules.infiniteResources) {
            notifyPowerVoidDestroyed(event.tile.x.toInt(), event.tile.y.toInt())
        }
    }

    @EventHandler
    fun onDangerousBlockBuildEvent(event: EventType.BlockBuildBeginEvent) {
        if (Vars.state.rules.infiniteResources ||
            event.breaking ||
            event.unit == null ||
            !event.unit.isPlayer) {
            return
        }

        val building = event.tile.build
        var block = event.tile.block()
        if (building is ConstructBlock.ConstructBuild) {
            block = building.current
        }

        if (!(block is Incinerator ||
            (block is NuclearReactor && Vars.state.rules.reactorExplosions))) {
            return
        }

        val x = ((event.tile.x + block.sizeOffset) - SEARCH_RADIUS) * Vars.tilesize * 1F
        val y = ((event.tile.y + block.sizeOffset) - SEARCH_RADIUS) * Vars.tilesize * 1F
        val size = ((SEARCH_RADIUS * 2) + block.size) * Vars.tilesize * 1F

        var found = false
        event.unit.player.team().data().buildingTree.intersect(x, y, size, size) { build ->
            if (build.isCoreBuilding) {
                found = true
            }
        }

        if (found) {
            DistributorProvider.get()
                .audienceProvider
                .getTeam(event.unit.player.team())
                .sendMessage(
                    announcement_dangerous_block_build(
                        event.unit.player.plainName(),
                        block,
                        event.tile.x.toInt(),
                        event.tile.y.toInt()))
        }
    }

    private fun notifyPowerVoidDestroyed(x: Int, y: Int) {
        DistributorProvider.get()
            .audienceProvider
            .players
            .sendMessage(announcement_power_void_destroyed(x, y))
    }

    private val Building.isCoreBuilding: Boolean
        get() = block() is CoreBlock || (this is StorageBlock.StorageBuild && linkedCore != null)

    companion object {
        private const val SEARCH_RADIUS = 5
    }
}
