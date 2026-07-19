// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.bridge

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.serialization.SerializableInstant
import java.util.UUID
import kotlin.time.Clock
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
        val timestamp: SerializableInstant = Clock.System.now(),
    )
}

@Inject
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
