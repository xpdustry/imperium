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

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.Lowercase
import com.xpdustry.imperium.common.control.RemoteActionMessage
import com.xpdustry.imperium.common.control.toExitStatus
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.await
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

class ServerCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discovery = instances.get<Discovery>()
    private val tracker = instances.get<PlayerTracker>()
    private val application = instances.get<ImperiumApplication>()
    private val messenger = instances.get<Messenger>()
    private val codec = instances.get<IdentifierCodec>()

    @ImperiumCommand(["server", "list"])
    suspend fun onServerList(interaction: SlashCommandInteraction) =
        interaction
            .replyEmbeds(
                Embed {
                    title = "Server List"
                    description = discovery.servers.values.joinToString(separator = "\n") { "- ${it.name}" }
                }
            )
            .await()

    @ImperiumCommand(["player", "joins"])
    suspend fun onServerPlayerJoin(interaction: SlashCommandInteraction, @Lowercase server: String) {
        val reply = interaction.deferReply(false).await()
        val joins = tracker.getPlayerJoins(server)
        if (joins == null) {
            reply.sendMessage("Server not found.").await()
            return
        }
        reply.sendMessageEmbeds(createPlayerListEmbed(joins, "Join")).await()
    }

    @ImperiumCommand(["player", "online"])
    suspend fun onServerPlayerOnline(interaction: SlashCommandInteraction, @Lowercase server: String? = null) {
        val reply = interaction.deferReply(false).await()
        if (server != null) {
            val online = tracker.getOnlinePlayers(server)
            if (online == null) {
                reply.sendMessage("Server not found.").await()
                return
            }
            reply.sendMessageEmbeds(createPlayerListEmbed(online, "Online", time = false)).await()
        } else {
            val embeds = mutableListOf<MessageEmbed>()
            for ((key, value) in discovery.servers) {
                if (value.data is Discovery.Data.Mindustry) {
                    val players = tracker.getOnlinePlayers(key) ?: emptyList()
                    if (players.isNotEmpty()) {
                        embeds += createPlayerListEmbed(players, "Online", time = false, server = key)
                    }
                }
            }
            if (embeds.isEmpty()) {
                reply.sendMessage("No players online at all :(").await()
            } else {
                reply.sendMessageEmbeds(embeds).await()
            }
        }
    }

    // TODO The following code works but its turbo shit, fixit!!
    @ImperiumCommand(["server", "control"], Rank.ADMIN)
    suspend fun onServerControl(
        interaction: SlashCommandInteraction,
        action: RemoteActionMessage.Action,
        @Lowercase server: String? = null,
        immediate: Boolean = false,
    ) {
        val reply = interaction.deferReply(false).await()
        if (server == "discord") {
            reply.sendMessage("Restarting discord bot.").await()
            application.exit(action.toExitStatus())
            return
        }
        if (server != null && discovery.servers[server] == null) {
            reply.sendMessage("Server not found.").await()
            return
        }
        messenger.publish(RemoteActionMessage(server, action, immediate))
        reply
            .sendMessage(
                "Sent ${action.name.lowercase()} request to " +
                    if (server == null) "**all servers**" else "server **$server**."
            )
            .await()
    }

    @ImperiumCommand(["exit"], Rank.OWNER)
    suspend fun onExit(interaction: SlashCommandInteraction) {
        interaction.reply("Exiting...").await()
        application.exit(ExitStatus.EXIT)
    }

    private fun createPlayerListEmbed(
        list: List<PlayerTracker.Entry>,
        name: String,
        time: Boolean = true,
        server: String? = null,
    ) = Embed {
        title = "Player $name List"
        if (server != null) {
            title += " in $server"
        }
        description = buildString {
            append("```\n")
            if (list.isEmpty()) {
                append("No players found.\n")
            }
            for (entry in list) {
                if (time) {
                    append(TIME_FORMAT.format(entry.timestamp.atOffset(ZoneOffset.UTC)))
                    append(" ")
                }
                append("#")
                append(codec.encode(entry.playerId))
                append(" ")
                append(entry.player.name)
                append("\n")
            }
            append("```")
        }
    }

    companion object {
        private val TIME_FORMAT =
            DateTimeFormatterBuilder()
                .appendValue(ChronoField.HOUR_OF_DAY, 2)
                .appendLiteral(':')
                .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                .appendLiteral(':')
                .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                .toFormatter(Locale.ENGLISH)
    }
}
