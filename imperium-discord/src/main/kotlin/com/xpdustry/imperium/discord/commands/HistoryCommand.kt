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
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.Lowercase
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.history.HistoryRequestMessage
import com.xpdustry.imperium.common.history.HistoryResponseMessage
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.request
import com.xpdustry.imperium.discord.misc.await
import kotlin.time.Duration.Companion.seconds
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.utils.FileUpload

class HistoryCommand(instances: InstanceManager) : ImperiumApplication.Listener {

    private val messenger = instances.get<Messenger>()
    private val codec = instances.get<IdentifierCodec>()

    @ImperiumCommand(["history"])
    suspend fun onHistoryCommand(
        interaction: SlashCommandInteraction,
        @Lowercase player: String,
        @Lowercase server: String,
    ) {
        val reply = interaction.deferReply(true).await()
        val identifier = codec.tryDecode(player)
        if (identifier == null) {
            reply.sendMessage("Invalid player id.").await()
            return
        }
        messenger
            .request<HistoryResponseMessage>(HistoryRequestMessage(server, identifier), timeout = 5.seconds)
            ?.history
            ?.let { reply.sendFiles(FileUpload.fromStreamSupplier("history.txt") { it.byteInputStream() }).await() }
            ?: reply.sendMessage("No history found.").await()
    }
}
