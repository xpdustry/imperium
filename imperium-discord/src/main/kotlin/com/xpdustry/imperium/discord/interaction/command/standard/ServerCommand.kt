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
package com.xpdustry.imperium.discord.interaction.command.standard

import com.xpdustry.imperium.common.account.User
import com.xpdustry.imperium.common.account.UserManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.MindustryPlayerMessage
import com.xpdustry.imperium.common.collection.LimitedList
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.discord.interaction.InteractionActor
import com.xpdustry.imperium.discord.interaction.Permission
import com.xpdustry.imperium.discord.interaction.command.Command
import org.javacord.api.entity.message.embed.EmbedBuilder
import java.net.InetAddress
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.Queue
import kotlin.random.Random
import kotlin.random.nextInt

class ServerCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discovery = instances.get<Discovery>()
    private val messenger = instances.get<Messenger>()
    private val history = mutableMapOf<String, Queue<PlayerJoinEntry>>()
    private val users = instances.get<UserManager>()

    override fun onImperiumInit() {
        messenger.subscribe<MindustryPlayerMessage> {
            if (it.action != MindustryPlayerMessage.Action.Join) return@subscribe
            history.computeIfAbsent(it.serverName) { LimitedList(30) }
                .add(PlayerJoinEntry(it.player, Random.nextInt(100000..999999)))
        }
    }

    @Command("server", "list", ephemeral = false)
    suspend fun onServerList(actor: InteractionActor) =
        actor.respond(
            EmbedBuilder()
                .setTitle("Server List")
                .setDescription(discovery.servers.joinToString(separator = "\n\n") { " - " + it.serverName }),
        )

    // TODO Make a better system that can list joins, current and left players
    @Command("server", "player", "joins", permission = Permission.MODERATOR)
    suspend fun onServerPlayerJoin(actor: InteractionActor, server: String) {
        val joins = history[server]
        if (joins == null) {
            actor.respond("Server not found.")
            return
        }

        val text = buildString {
            append("```\n")
            if (joins.isEmpty()) {
                append("No players have joined this server yet.\n")
            }
            for (entry in joins) {
                DateTimeFormatter.ISO_LOCAL_TIME
                append(TIME_FORMAT.format(entry.timestamp.atOffset(ZoneOffset.UTC)))
                append(" #")
                append(entry.tid)
                append(" ")
                append(entry.player.name)
                append("\n")
            }
            append("```")
        }

        actor.respond(EmbedBuilder().setTitle("Player Join List").setDescription(text))
    }

    // TODO Move into a dedicated command PlayerCommand
    @Command("player", "info", permission = Permission.MODERATOR)
    suspend fun onPlayerInfo(actor: InteractionActor, id: String) {
        val user: User?
        val tid = id.toIntOrNull()
        if (tid in 100000..999999) {
            val entry = history.values.flatten().firstOrNull { it.tid == tid }
            if (entry == null) {
                actor.respond("Player temporary id not found.")
                return
            }
            user = users.findByUuidOrCreate(entry.player.uuid)
        } else {
            user = users.findByUuid(id)
            if (user == null) {
                actor.respond("Player uuid not found.")
                return
            }
        }

        DateTimeFormatter.ISO_LOCAL_DATE_TIME

        actor.respond(
            EmbedBuilder()
                .setTitle("Player Info")
                .addField("Last Name", user.lastName, true)
                .addField("Last Address", user.lastAddress?.hostAddress!!, true)
                .addField("Uuid", "`${user._id}`", true)
                .addField("First Join", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(user.firstJoin.atOffset(ZoneOffset.UTC)), true)
                .addField("Last Join", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(user.lastJoin.atOffset(ZoneOffset.UTC)), true)
                .addField("Times Joined", user.timesJoined.toString(), true)
                .addField("Names", user.names.joinToString())
                .addField("Addresses", user.addresses.joinToString(transform = InetAddress::getHostAddress)),
        )
    }

    data class PlayerJoinEntry(val player: Identity.Mindustry, val tid: Int, val timestamp: Instant = Instant.now())

    companion object {
        private val TIME_FORMAT = DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter(Locale.ENGLISH)
    }
}
