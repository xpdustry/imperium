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
import com.xpdustry.imperium.common.control.RestartMessage
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.misc.Embed
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import net.dv8tion.jda.api.entities.MessageEmbed

class ServerCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discovery = instances.get<Discovery>()
    private val tracker = instances.get<PlayerTracker>()
    private val application = instances.get<ImperiumApplication>()
    private val messenger = instances.get<Messenger>()

    @ImperiumCommand(["server", "list"])
    @NonEphemeral
    suspend fun onServerList(actor: InteractionSender.Slash) =
        actor.respond(
            Embed {
                title = "Server List"
                description =
                    discovery.servers.values.joinToString(separator = "\n") { "- ${it.name}" }
            })

    @ImperiumCommand(["player", "joins"])
    @NonEphemeral
    suspend fun onServerPlayerJoin(actor: InteractionSender.Slash, server: String) {
        val joins = tracker.getPlayerJoins(server)
        if (joins == null) {
            actor.respond("Server not found.")
            return
        }
        actor.respond(createPlayerListEmbed(joins, "Join"))
    }

    @ImperiumCommand(["player", "online"])
    @NonEphemeral
    suspend fun onServerPlayerOnline(actor: InteractionSender.Slash, server: String? = null) {
        if (server != null) {
            val online = tracker.getOnlinePlayers(server)
            if (online == null) {
                actor.respond("Server not found.")
                return
            }
            actor.respond(createPlayerListEmbed(online, "Online", time = false))
        } else {
            val embeds = mutableListOf<MessageEmbed>()
            for ((key, value) in discovery.servers) {
                if (value.data is Discovery.Data.Mindustry) {
                    val players = tracker.getOnlinePlayers(key) ?: emptyList()
                    if (players.isNotEmpty()) {
                        embeds +=
                            createPlayerListEmbed(players, "Online", time = false, server = key)
                    }
                }
            }
            actor.respond { addEmbeds(embeds) }
        }
    }

    @ImperiumCommand(["server", "restart"], Rank.ADMIN)
    @NonEphemeral
    suspend fun onServerRestart(
        actor: InteractionSender.Slash,
        server: String,
        immediate: Boolean = false
    ) {
        if (server == "discord") {
            actor.respond("Restarting discord bot.")
            application.exit(ExitStatus.RESTART)
            return
        } else {
            if (discovery.servers[server] == null) {
                actor.respond("Server not found.")
                return
            }
            messenger.publish(RestartMessage(server, immediate))
            actor.respond("Sent restart request to server **$server**.")
        }
    }

    private fun createPlayerListEmbed(
        list: List<PlayerTracker.Entry>,
        name: String,
        time: Boolean = true,
        server: String? = null
    ): MessageEmbed {
        return Embed {
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
                    append(entry.snowflake)
                    append(" ")
                    append(entry.player.name)
                    append("\n")
                }
                append("```")
            }
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
