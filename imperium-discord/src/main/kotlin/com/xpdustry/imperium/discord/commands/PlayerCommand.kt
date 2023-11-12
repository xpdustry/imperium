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

import com.xpdustry.imperium.common.account.Role
import com.xpdustry.imperium.common.account.UserManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.service.DiscordService
import java.net.InetAddress
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toCollection
import org.bson.types.ObjectId
import org.javacord.api.entity.message.embed.EmbedBuilder

class PlayerCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val tracker = instances.get<PlayerTracker>()
    private val users = instances.get<UserManager>()
    private val discord = instances.get<DiscordService>()

    @Command(["player", "info"])
    suspend fun onPlayerInfo(actor: InteractionSender, query: String) {
        val user =
            users.findByUuid(query)
                ?: query
                    .toLongOrNull()
                    ?.let { tracker.getPlayerEntry(it) }
                    ?.let { users.findByUuid(it.player.uuid) }
                    ?: if (ObjectId.isValid(query)) users.findById(ObjectId(query)) else null
        if (user == null) {
            actor.respond("Player not found.")
            return
        }

        actor.respond(
            EmbedBuilder()
                .setTitle("Player Info")
                .addField("ID", "`${user._id}`", true)
                .addField("Last Name", "`${user.lastName}`", true)
                .addField("Names", user.names.joinToString(transform = { "`$it`" }), true)
                .addField(
                    "First Join",
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                        user.firstJoin.atOffset(ZoneOffset.UTC)),
                    true)
                .addField(
                    "Last Join",
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                        user.lastJoin.atOffset(ZoneOffset.UTC)),
                    true)
                .addField("Times Joined", user.timesJoined.toString(), true)
                .apply {
                    if (discord.isAllowed(actor.user, Role.ADMINISTRATOR)) {
                        addField("Uuid", "`${user.uuid}`", true)
                        addField("Last Address", user.lastAddress!!.hostAddress, true)
                        addField(
                            "Addresses",
                            user.addresses.joinToString(transform = InetAddress::getHostAddress),
                            true)
                    }
                },
        )
    }

    @Command(["player", "search"])
    @NonEphemeral
    suspend fun onPlayerSearch(actor: InteractionSender, query: String) {
        val users = users.searchUser(query).take(21).toCollection(mutableListOf())
        if (users.isEmpty()) {
            actor.respond("No players found.")
            return
        }
        var hasMore = false
        if (users.size > 20) {
            hasMore = true
            users.removeLast()
        }

        var text =
            if (users.isEmpty()) {
                "No players found."
            } else {
                users.joinToString(separator = "\n") {
                    "- `${it.lastName!!.stripMindustryColors()}` (`${it._id}`)"
                }
            }
        if (hasMore) {
            text += "\n\nAnd more..."
        }

        actor.respond(EmbedBuilder().setTitle("Player Search").setDescription(text))
    }
}
