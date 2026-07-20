// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.commands

import com.xpdustry.imperium.backend.misc.await
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.Lowercase
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.history.HistoryRequestMessage
import com.xpdustry.imperium.common.history.HistoryResponseMessage
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.user.PlayerIDLike
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.utils.FileUpload

suspend fun getPlayerHistory(messenger: MessageService, server: String, player: Int): String? {
    val deferred = CompletableDeferred<String>()
    val id = UUID.randomUUID().toString()
    val subscription = messenger.subscribe<HistoryResponseMessage> { if (it.id == id) deferred.complete(it.history) }
    try {
        messenger.broadcast(HistoryRequestMessage(server, player, id))
        return withTimeoutOrNull(2.seconds) { deferred.await() }
    } finally {
        subscription.cancel()
    }
}

@Inject
class HistoryCommand constructor(private val messenger: MessageService) : ImperiumApplication.Listener {

    @ImperiumCommand(["history"])
    suspend fun onHistoryCommand(
        interaction: SlashCommandInteraction,
        player: PlayerIDLike,
        @Lowercase server: String,
    ) {
        val reply = interaction.deferReply(true).await()
        val history = getPlayerHistory(messenger, server, player.id)
        if (history == null) {
            reply.sendMessage("No history found.").await()
        } else {
            reply.sendFiles(FileUpload.fromStreamSupplier("history.txt") { history.byteInputStream() }).await()
        }
    }
}
