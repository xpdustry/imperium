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
package com.xpdustry.imperium.common.bridge

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.serialization.SerializableJInstant
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

interface PlayerTracker {
    suspend fun getPlayerJoins(server: String): List<Entry>?

    suspend fun getOnlinePlayers(server: String): List<Entry>?

    @Serializable
    data class Entry(
        val player: Identity.Mindustry,
        val playerId: Int,
        val timestamp: SerializableJInstant = Instant.now(),
    )
}

open class RequestingPlayerTracker(protected val messenger: MessageService) :
    PlayerTracker, ImperiumApplication.Listener {

    override suspend fun getPlayerJoins(server: String): List<PlayerTracker.Entry>? =
        requestPlayerList(server, PlayerListRequest.Type.JOIN)

    override suspend fun getOnlinePlayers(server: String): List<PlayerTracker.Entry>? =
        requestPlayerList(server, PlayerListRequest.Type.ONLINE)

    private suspend fun requestPlayerList(server: String, type: PlayerListRequest.Type): List<PlayerTracker.Entry>? {
        val deferred = CompletableDeferred<List<PlayerTracker.Entry>>()
        val id = UUID.randomUUID().toString()
        val subscription = messenger.subscribe<PlayerListResponse> { if (it.id == id) deferred.complete(it.entries) }
        try {
            messenger.broadcast(PlayerListRequest(server, type, id))
            return withTimeoutOrNull(2.seconds) { deferred.await() }
        } finally {
            subscription.cancel()
        }
    }

    @Serializable
    protected data class PlayerListRequest(val server: String, val type: Type, val id: String) : Message {
        enum class Type {
            JOIN,
            ONLINE,
        }
    }

    @Serializable
    protected data class PlayerListResponse(val entries: List<PlayerTracker.Entry>, val id: String) : Message
}
