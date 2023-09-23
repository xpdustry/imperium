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
package com.xpdustry.imperium.mindustry.map

import arc.files.Fi
import arc.func.Cons
import arc.util.Log
import arc.util.Strings
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.core.GameState
import mindustry.game.EventType.GameOverEvent
import mindustry.game.Gamemode
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.io.MapIO
import mindustry.maps.Map
import mindustry.net.Administration
import mindustry.net.Packets.KickReason
import mindustry.server.ServerControl
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

// TODO
//  - Override map commands to allow imperium integration (maps, reloadmaps, host)
//  - Add gamemode field to maps
class MapListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Mindustry>()
    private val maps = instances.get<MindustryMapManager>()
    private val cache = instances.get<Path>("directory").resolve("map-cache")
    private val index = AtomicInteger(0)

    override fun onImperiumInit() {
        if (cache.notExists()) cache.createDirectory()
        ServerControl.instance.gameOverListener = Cons(::onGameOver)
    }

    private fun onGameOver(event: GameOverEvent) {
        if (Vars.state.rules.waves) {
            Log.info(
                "Game over! Reached wave @ with @ players online on map @.",
                Vars.state.wave,
                Groups.player.size(),
                Strings.capitalize(Vars.state.map.plainName()),
            )
        } else {
            Log.info(
                "Game over! Team @ is victorious with @ players online on map @.",
                event.winner.name,
                Groups.player.size(),
                Strings.capitalize(Vars.state.map.plainName()),
            )
        }

        ImperiumScope.MAIN.launch {
            val map = try {
                getNextMap(ServerControl.instance.lastMode, Vars.state.map)
            } catch (e: Exception) {
                logger.error("Failed to load next map, closing the server.", e)
                null
            }

            runMindustryThread {
                if (map == null) {
                    Vars.netServer.kickAll(KickReason.gameover)
                    Vars.state.set(GameState.State.menu)
                    Vars.net.closeServer()
                    return@runMindustryThread
                }

                Call.infoMessage(
                    """
                    ${if (Vars.state.rules.pvp) "[accent]The " + event.winner.coloredName() + " team is victorious![]\n" else "[scarlet]Game over![]\n"}
                    Next selected map: [accent]${map.name()}[white]${if (map.hasTag("author")) " by[accent] " + map.author() + "[white]" else ""}.
                    New game begins in ${Administration.Config.roundExtraTime.num()} seconds.
                    """.trimIndent(),
                )

                Vars.state.gameOver = true
                Call.updateGameOver(event.winner)
                Log.info("Selected next map to be @.", map.plainName())
                ServerControl.instance.play {
                    Vars.world.loadMap(map, map.applyRules(ServerControl.instance.lastMode))
                }
            }
        }
    }

    private suspend fun getNextMap(mode: Gamemode, previous: Map): Map? {
        val pool = maps.findMapsByServer(config.name).toCollection(mutableListOf())
        pool.sortBy(MindustryMap::name)

        if (pool.isEmpty()) {
            logger.warn("No maps found in server pool, falling back to local maps.")
            return Vars.maps.getNextMap(mode, previous)
        }

        try {
            // TODO What if index is out of range?
            val map = pool[index.getAndIncrement() % pool.size]
            val file = cache.resolve("${map._id.toHexString()}_${map.lastUpdate.toEpochMilli()}.msav")
            if (file.notExists()) {
                logger.debug("Downloading map {} (id={}) from serer pool.", map.name, map._id.toHexString())
                file.outputStream().use { output ->
                    maps.getMapObject(map._id)!!.getStream().use { input -> input.copyTo(output) }
                }
            }

            logger.info("Loaded map {} (id={}) from server pool.", map.name, map._id.toHexString())
            return MapIO.createMap(Fi(file.toFile()), true).also {
                it.tags.put("imperium-map-id", map._id.toHexString())
            }
        } catch (e: Exception) {
            logger.error("Failed to load map from server pool, falling back to local maps.", e)
            return Vars.maps.getNextMap(mode, previous)
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
