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
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.discord.bridge.PlayerHistory
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import org.javacord.api.entity.message.embed.EmbedBuilder

class ServerCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discovery = instances.get<Discovery>()
    private val history = instances.get<PlayerHistory>()

    @Command(["server", "list"])
    @NonEphemeral
    suspend fun onServerList(actor: InteractionSender) =
        actor.respond(
            EmbedBuilder()
                .setTitle("Server List")
                .setDescription(
                    discovery.servers.joinToString(separator = "\n") { " - " + it.serverName }),
        )

    @Command(["server", "player", "joins"])
    @NonEphemeral
    suspend fun onServerPlayerJoin(actor: InteractionSender, server: String) {
        val joins = history.getPlayerJoins(server)
        if (joins == null) {
            actor.respond("Server not found.")
            return
        }
        onServerPlayerList(actor, joins, "Join")
    }

    @Command(["server", "player", "quits"])
    @NonEphemeral
    suspend fun onServerPlayerQuit(actor: InteractionSender, server: String) {
        val quits = history.getPlayerQuits(server)
        if (quits == null) {
            actor.respond("Server not found.")
            return
        }
        onServerPlayerList(actor, quits, "Quit")
    }

    private suspend fun onServerPlayerList(
        actor: InteractionSender,
        list: List<PlayerHistory.Entry>,
        name: String
    ) {
        val text = buildString {
            append("```\n")
            if (list.isEmpty()) {
                append("No players found.\n")
            }
            for (entry in list) {
                append(TIME_FORMAT.format(entry.timestamp.atOffset(ZoneOffset.UTC)))
                append(" #")
                append(entry.tid)
                append(" ")
                append(entry.player.name)
                append("\n")
            }
            append("```")
        }

        actor.respond(EmbedBuilder().setTitle("Player $name List").setDescription(text))
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
