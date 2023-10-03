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
package com.xpdustry.imperium.mindustry.world

import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.kotlin.extension.buildAndRegister
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.geometry.Cluster
import com.xpdustry.imperium.common.geometry.ClusterManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.misc.registerCopy
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.event.EventHandler
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.storage.CoreBlock

// TODO
//  - Add Core Alerts option to not interfere with other plugins
//  - Add Core damage alerts
class CoreBlockListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val managers = mutableMapOf<Team, ClusterManager<Unit>>()
    private val clientCommands = instances.get<ImperiumPluginCommandManager>("client")

    override fun onImperiumInit() {
        clientCommands.buildAndRegister("core") {
            registerCopy("list") {
                handler { ctx ->
                    val clusters = getManager(ctx.sender.player.team()).clusters
                    if (clusters.isEmpty()) {
                        ctx.sender.sendMessage("No cores found")
                    } else {
                        ctx.sender.sendMessage(
                            buildString {
                                append("Found ${clusters.size} cores: ")
                                for ((index, cluster) in clusters.withIndex()) {
                                    append("\n - #${index + 1} ${cluster.x}, ${cluster.y} (${cluster.w}x${cluster.h})")
                                }
                            },
                        )
                    }
                }
            }

            registerCopy("teleport", aliases = arrayOf("tp")) {
                argument(IntegerArgument.builder<CommandSender>("id").withMin(1))
                handler { ctx ->
                    val clusters = getManager(ctx.sender.player.team()).clusters
                    if (clusters.isEmpty()) {
                        ctx.sender.sendMessage("No cores found")
                    } else {
                        val cluster = clusters.getOrNull(ctx.get<Int>("id") - 1)
                        if (cluster == null) {
                            ctx.sender.sendMessage("Invalid core id")
                        } else {
                            val core = cluster.blocks.first()
                            Call.playerSpawn(Vars.world.tile(core.x, core.y), ctx.sender.player)
                            ctx.sender.sendMessage("Teleported to core at ${cluster.x}, ${cluster.y}")
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    fun onPlayEvent(event: EventType.PlayEvent) {
        Vars.world.tiles.eachTile {
            val building = it.build
            if (building is CoreBlock.CoreBuild) {
                val manager = getManager(building.team)
                if (manager.getElement(building.rx, building.ry) != null) {
                    return@eachTile
                }
                manager.addElement(
                    Cluster.Block(
                        building.rx,
                        building.ry,
                        building.block.size,
                        Unit,
                    ),
                )
                logger.trace("Loaded {} core at ({}, {})", building.team.name, building.rx, building.ry)
            }
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
        logger.trace("Changed core team at ({}, {}) from {} to {}", building.rx, building.ry, event.previous.name, building.team.name)
        getManager(event.previous).removeElement(building.rx, building.ry)
        getManager(building.team).addElement(
            Cluster.Block(
                building.rx,
                building.ry,
                building.block.size,
                Unit,
            ),
        )
    }

    @EventHandler
    fun onBlockBuildEvent(event: EventType.BlockBuildEndEvent) {
        var building = event.tile.build
        if (event.breaking && building is ConstructBlock.ConstructBuild) {
            building = building.prevBuild.first()
        }

        if (building is CoreBlock.CoreBuild) {
            if (event.breaking) {
                val manager = getManager(building.team)
                manager.removeElement(building.rx, building.ry)
                logger.trace("Removed {} core at ({}, {})", building.team.name, building.rx, building.ry)
            } else {
                val manager = getManager(building.team)
                manager.addElement(
                    Cluster.Block(
                        building.rx,
                        building.ry,
                        building.block.size,
                        Unit,
                    ),
                )
                logger.trace("Added {} core at ({}, {})", building.team.name, building.rx, building.ry)
            }
        }
    }

    private fun getManager(team: Team) = managers.getOrPut(team) { ClusterManager { _, _ -> } }

    // Goofy ass Mindustry coordinate system
    private val Building.rx: Int get() = tileX() + block.sizeOffset
    private val Building.ry: Int get() = tileY() + block.sizeOffset

    companion object {
        private val logger by LoggerDelegate()
    }
}
