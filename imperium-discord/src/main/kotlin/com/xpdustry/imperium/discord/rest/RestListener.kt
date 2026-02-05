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
package com.xpdustry.imperium.discord.rest

import com.sun.net.httpserver.HttpServer
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.serialization.SerializableInetAddress
import com.xpdustry.imperium.common.version.MindustryVersion
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RestListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val discovery = instances.get<Discovery>()
    private val tracker = instances.get<PlayerTracker>()
    private val config = instances.get<ImperiumConfig>()
    private val codec = instances.get<IdentifierCodec>()

    private lateinit var server: HttpServer

    override fun onImperiumInit() {
        server = HttpServer.create(InetSocketAddress(InetAddress.ofLiteral("0.0.0.0"), config.webserver.port), 0)
        server.createContext("/v0/servers") { exchange ->
            if (exchange.requestMethod != "GET" || exchange.requestURI.path != "/v0/servers") {
                exchange.sendResponseHeaders(400, -1)
                exchange.responseBody.close()
                return@createContext
            }
            try {
                val json = runBlocking { Json.encodeToString(getServerEntries()) }
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.writer().use { it.write(json) }
            } catch (e: Exception) {
                exchange.sendResponseHeaders(500, -1)
                exchange.responseBody.close()
                logger.error("Failed to respond to a /v0/servers request", e)
            }
        }
        server.start()
    }

    private suspend fun getServerEntries() =
        discovery.servers
            .asSequence()
            .filter { it.value.data is Discovery.Data.Mindustry }
            .toList()
            .map { (name, value) ->
                val data = value.data as Discovery.Data.Mindustry
                ServerEntry(
                    name = name,
                    host = data.host,
                    port = data.port,
                    mapName = data.mapName,
                    description = data.description,
                    wave = data.wave,
                    playerCount = data.playerCount,
                    playerLimit = data.playerLimit,
                    gameVersion = data.gameVersion,
                    gamemode = data.gamemodeName ?: data.gamemode.name.lowercase(),
                    active = data.state != Discovery.Data.Mindustry.State.STOPPED,
                    players =
                        tracker.getOnlinePlayers(name)?.map { player ->
                            ServerEntry.Player(player.player.displayName, codec.encode(player.playerId))
                        } ?: emptyList(),
                )
            }

    override fun onImperiumExit() {
        server.stop(5)
    }

    @Serializable
    data class ServerEntry(
        val name: String,
        val host: SerializableInetAddress,
        val port: Int,
        val mapName: String,
        val description: String,
        val wave: Int,
        val playerCount: Int,
        val playerLimit: Int,
        val gameVersion: MindustryVersion,
        val gamemode: String,
        val active: Boolean,
        val players: List<Player>,
    ) {
        @Serializable data class Player(val name: String, val id: String)
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
