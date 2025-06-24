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
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TriggerHandler
import com.xpdustry.distributor.api.collection.MindustryCollections
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.mindustry.misc.isCoreBuilding
import com.xpdustry.imperium.mindustry.misc.isSourceBlock
import com.xpdustry.imperium.mindustry.translation.announcement_dangerous_block_build
import com.xpdustry.imperium.mindustry.translation.announcement_impending_explosion_alert
import com.xpdustry.imperium.mindustry.translation.announcement_important_block_destroy_attempt
import com.xpdustry.imperium.mindustry.translation.announcement_important_block_destroyed
import mindustry.Vars
import mindustry.game.EventType
import mindustry.net.Administration.ActionType
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.ConstructBlock.ConstructBuild
import mindustry.world.blocks.power.ConsumeGenerator
import mindustry.world.blocks.power.ConsumeGenerator.ConsumeGeneratorBuild
import mindustry.world.blocks.power.NuclearReactor
import mindustry.world.blocks.production.Incinerator
import mindustry.world.consumers.ConsumeItemExplode

class AlertListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val explosives = MindustryCollections.immutableList(Vars.content.items()).filter { it.explosiveness > 0 }
    private val generators = IntSet()
    private val generatorsRateLimiter =
        SimpleRateLimiter<Int>(1, instances.get<ImperiumConfig>().mindustry.world.explosiveDamageAlertDelay)

    override fun onImperiumInit() {
        Vars.netServer.admins.addActionFilter {
            if (
                ((it.type == ActionType.breakBlock && it.block.isSourceBlock) ||
                    (it.type == ActionType.placeBlock && it.tile.block()?.isSourceBlock == true)) &&
                    !Vars.state.rules.infiniteResources
            ) {
                val block =
                    when (it.type) {
                        ActionType.breakBlock -> it.block
                        ActionType.placeBlock -> it.tile.block()!!
                        else -> error("That ain't right")
                    }
                Distributor.get()
                    .audienceProvider
                    .getTeam(it.player.team())
                    .sendMessage(
                        announcement_important_block_destroy_attempt(
                            it.player,
                            block,
                            it.tile.x.toInt(),
                            it.tile.y.toInt(),
                        )
                    )
                return@addActionFilter false
            }
            true
        }
    }

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
        if (
            (!Vars.state.rules.reactorExplosions ||
                (Vars.state.rules.infiniteResources && !Vars.state.rules.damageExplosions))
        )
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
                if (
                    building.items.has(item) &&
                        consumers.any { item.explosiveness > it.threshold } &&
                        generatorsRateLimiter.incrementAndCheck(pos)
                ) {
                    Distributor.get()
                        .audienceProvider
                        .getTeam(building.team())
                        .sendMessage(announcement_impending_explosion_alert(block, x, y))
                    break
                }
            }
        }
    }

    @EventHandler
    fun onSourceBlockDestroy(event: EventType.BlockDestroyEvent) {
        if (Vars.state.rules.infiniteResources) return
        if (event.tile.block().isSourceBlock) {
            Distributor.get()
                .audienceProvider
                .getTeam(event.tile.team())
                .sendMessage(
                    announcement_important_block_destroyed(
                        event.tile.block(),
                        event.tile.x.toInt(),
                        event.tile.y.toInt(),
                    )
                )
        }
    }

    @EventHandler
    fun onSourceBlockDelete(event: EventType.BlockBuildBeginEvent) {
        if (Vars.state.rules.infiniteResources) return
        val building = event.tile.build
        if (event.breaking && building is ConstructBuild && building.current.isSourceBlock) {
            Distributor.get()
                .audienceProvider
                .getTeam(building.team())
                .sendMessage(
                    announcement_important_block_destroyed(building.current, event.tile.x.toInt(), event.tile.y.toInt())
                )
        }
    }

    @EventHandler
    fun onDangerousBlockBuild(event: EventType.BlockBuildBeginEvent) {
        if (Vars.state.rules.infiniteResources || event.breaking || event.unit == null || !event.unit.isPlayer) {
            return
        }

        val building = event.tile.build
        var block = event.tile.block()
        if (building is ConstructBlock.ConstructBuild) {
            block = building.current
        }

        if (!(block is Incinerator || (block is NuclearReactor && Vars.state.rules.reactorExplosions))) {
            return
        }

        val x = ((event.tile.x + block.sizeOffset) - CORE_SEARCH_RADIUS) * Vars.tilesize * 1F
        val y = ((event.tile.y + block.sizeOffset) - CORE_SEARCH_RADIUS) * Vars.tilesize * 1F
        val size = ((CORE_SEARCH_RADIUS * 2) + block.size) * Vars.tilesize * 1F

        var found = false
        event.unit.player.team().data().buildingTree.intersect(x, y, size, size) { build ->
            if (build.isCoreBuilding) {
                found = true
            }
        }

        if (found) {
            Distributor.get()
                .audienceProvider
                .getTeam(event.unit.player.team())
                .sendMessage(
                    announcement_dangerous_block_build(
                        event.unit.player.plainName(),
                        block,
                        event.tile.x.toInt(),
                        event.tile.y.toInt(),
                    )
                )
        }
    }

    companion object {
        private const val CORE_SEARCH_RADIUS = 5
    }
}
