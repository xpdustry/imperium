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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.serialization.SerializableInetAddress
import com.xpdustry.imperium.common.version.MindustryVersion
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable

class RestListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val discovery = instances.get<Discovery>()
    private val tracker = instances.get<PlayerTracker>()
    private val config = instances.get<ImperiumConfig>()
    private val codec = instances.get<IdentifierCodec>()

    private lateinit var ktor: EmbeddedServer<*, *>

    override fun onImperiumInit() {
        ktor =
            embeddedServer(Netty, port = config.webserver.port) {
                    install(ContentNegotiation) { json() }
                    routing { route("/v0") { get("/servers") { call.respond(getServerEntries()) } } }
                }
                .start(wait = false)
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
        ktor.stop(10L, 10L, TimeUnit.SECONDS)
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
}
