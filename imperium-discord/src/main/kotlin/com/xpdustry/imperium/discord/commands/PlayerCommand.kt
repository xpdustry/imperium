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

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.snowflake.timestamp
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.service.DiscordService
import java.net.InetAddress
import org.javacord.api.entity.message.embed.EmbedBuilder

class PlayerCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val discord = instances.get<DiscordService>()
    private val renderer = instances.get<TimeRenderer>()

    @Command(["player", "info"])
    suspend fun onPlayerInfoCommand(actor: InteractionSender, player: String) {
        // TODO THIS IS GOOFY, REPLACE WITH A PROPER PARSER WHEN CLOUD 2
        var user = users.findByUuid(player)
        if (user == null && player.toLongOrNull() != null) {
            user = users.findBySnowflake(player.toLong())
        }
        if (user == null) {
            actor.respond("Player not found.")
            return
        }
        val details = users.findNamesAndAddressesBySnowflake(user.snowflake)
        actor.respond(
            EmbedBuilder()
                .setTitle("Player Info")
                .addField("ID", "`${user.snowflake}`", true)
                .addField("Last Name", "`${user.lastName}`", true)
                .addField("Names", details.names.joinToString(transform = { "`$it`" }), true)
                .addField("First Join", renderer.renderInstant(user.snowflake.timestamp), true)
                .addField("Last Join", renderer.renderInstant(user.lastJoin), true)
                .addField("Times Joined", user.timesJoined.toString(), true)
                .apply {
                    if (discord.isAllowed(actor.user, Rank.ADMIN)) {
                        addField("Uuid", "`${user.uuid}`", true)
                        addField("Last Address", user.lastAddress.hostAddress, true)
                        addField(
                            "Addresses",
                            details.addresses.joinToString(transform = InetAddress::getHostAddress),
                            true)
                    }
                },
        )
    }

    @Command(["player", "search"])
    @NonEphemeral
    suspend fun onPlayerSearch(actor: InteractionSender, query: String) {
        val users = users.searchUserByName(query).take(21).toCollection(mutableListOf())
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
                    "- `${it.lastName.stripMindustryColors()}` (`${it.snowflake}`)"
                }
            }
        if (hasMore) {
            text += "\n\nAnd more..."
        }

        actor.respond(EmbedBuilder().setTitle("Player Search").setDescription(text))
    }
}
