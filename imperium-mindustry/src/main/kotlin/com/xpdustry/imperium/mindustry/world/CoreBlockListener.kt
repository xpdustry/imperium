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
package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.geometry.Cluster
import com.xpdustry.imperium.common.geometry.ClusterManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.misc.Entities
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.storage.CoreBlock
import org.incendo.cloud.annotation.specifier.Range

class CoreBlockListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val managers = mutableMapOf<Team, ClusterManager<Unit>>()
    private val config = instances.get<ImperiumConfig>()
    private val damageRateLimiter =
        SimpleRateLimiter<CoreClusterDamageKey>(1, config.mindustry.world.coreDamageAlertDelay)

    override fun onImperiumInit() {
        if (!config.mindustry.world.displayCoreId) return
        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(1.seconds)
                if (!Vars.state.isPlaying) continue
                for (player in Entities.getPlayersAsync()) {
                    for ((index, cluster) in getManager(player.team()).clusters.withIndex()) {
                        Call.label(
                            player.con,
                            "#${index + 1}",
                            1F,
                            (cluster.x + (cluster.w / 2F)) * Vars.tilesize - (Vars.tilesize / 2F),
                            (cluster.y + (cluster.h / 2F)) * Vars.tilesize - (Vars.tilesize / 2F),
                        )
                    }
                }
            }
        }
    }

    @ImperiumCommand(["core", "list"])
    @ClientSide
    fun onCoreListCommand(sender: CommandSender) {
        val clusters = getManager(sender.player.team()).clusters
        if (clusters.isEmpty()) {
            sender.error("No cores found")
            return
        }
        sender.reply(
            buildString {
                appendLine("Found ${clusters.size} cores: ")
                for ((index, cluster) in clusters.withIndex()) {
                    appendLine("- #${index + 1} ${cluster.x}, ${cluster.y} (${cluster.w}x${cluster.h})")
                }
            }
        )
    }

    @ImperiumCommand(["core", "tp"])
    @ClientSide
    fun onCoreTeleportCommand(sender: CommandSender, @Range(min = "1") id: Int) {
        val clusters = getManager(sender.player.team()).clusters
        if (clusters.isEmpty()) {
            sender.error("No cores found")
            return
        }
        val cluster = clusters.getOrNull(id - 1)
        if (cluster == null) {
            sender.error("Invalid core id")
            return
        }
        val core = cluster.blocks.first()
        Call.playerSpawn(Vars.world.tile(core.x, core.y), sender.player)
        sender.reply("Teleported to core at ${cluster.x}, ${cluster.y}")
    }

    @EventHandler
    fun onBuildDamageEvent(event: EventType.BuildDamageEvent) {
        val building = event.build
        if (building !is CoreBlock.CoreBuild) return

        val manager = getManager(building.team)
        val (cluster, _) = manager.getElement(building.rx, building.ry) ?: return
        if (!damageRateLimiter.incrementAndCheck(CoreClusterDamageKey(building.team, cluster.x, cluster.y))) return

        val index = manager.clusters.indexOf(cluster)
        for (player in Entities.getPlayers()) {
            if (player.team() == building.team) {
                player.sendMessage(
                    "[scarlet]The core cluster [orange]#${index + 1}[] at ([orange]${cluster.x}[], [orange]${cluster.y}[]) is under attack!"
                )
            }
        }
    }

    @EventHandler
    fun onMenuToPlayEvent(event: MenuToPlayEvent) {
        managers.clear()
        Vars.world.tiles.eachTile { tile ->
            val building = tile.build
            if (building !is CoreBlock.CoreBuild) {
                return@eachTile
            }
            val manager = getManager(building.team)
            if (manager.getElement(building.rx, building.ry) != null) {
                return@eachTile
            }
            manager.addElement(Cluster.Block(building.rx, building.ry, building.block.size, Unit))
            logger.trace("Loaded {} core at ({}, {})", building.team.name, building.rx, building.ry)
        }
    }

    @EventHandler
    fun onBlockDestroyEvent(event: EventType.BlockDestroyEvent) {
        val building = event.tile.build
        if (building is CoreBlock.CoreBuild) {
            val manager = getManager(building.team)
            manager.removeElement(building.rx, building.ry)
            logger.trace("Destroyed {} core at ({}, {})", building.team.name, building.rx, building.ry)
        }
    }

    @EventHandler
    fun onTeamChangeEvent(event: EventType.BuildTeamChangeEvent) {
        val building = event.build
        if (building !is CoreBlock.CoreBuild) return
        logger.trace(
            "Changed core team at ({}, {}) from {} to {}",
            building.rx,
            building.ry,
            event.previous.name,
            building.team.name,
        )
        getManager(event.previous).removeElement(building.rx, building.ry)
        getManager(building.team).addElement(Cluster.Block(building.rx, building.ry, building.block.size, Unit))
    }

    @EventHandler
    fun onBlockBuildEvent(event: EventType.BlockBuildEndEvent) {
        var building = event.tile.build
        if (event.breaking && building is ConstructBlock.ConstructBuild && building.prevBuild?.isEmpty == false) {
            building = building.prevBuild.first()
        }

        if (building is CoreBlock.CoreBuild) {
            val manager = getManager(building.team)
            manager.removeElement(building.rx, building.ry)
            if (event.breaking) {
                logger.trace("Removed {} core at ({}, {})", building.team.name, building.rx, building.ry)
            } else {
                manager.addElement(Cluster.Block(building.rx, building.ry, building.block.size, Unit))
                logger.trace("Added {} core at ({}, {})", building.team.name, building.rx, building.ry)
            }
        }
    }

    private fun getManager(team: Team) = managers.getOrPut(team) { ClusterManager { _, _ -> } }

    // Goofy ass Mindustry coordinate system
    private val Building.rx: Int
        get() = tileX() + block.sizeOffset

    private val Building.ry: Int
        get() = tileY() + block.sizeOffset

    companion object {
        private val logger by LoggerDelegate()
    }

    data class CoreClusterDamageKey(val team: Team, val x: Int, val y: Int)
}
