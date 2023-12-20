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
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.service.DiscordService

class PlayerCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val discord = instances.get<DiscordService>()
    private val renderer = instances.get<TimeRenderer>()

    @Command(["player", "info"])
    suspend fun onPlayerInfoCommand(actor: InteractionSender.Slash, player: String) {
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
            Embed {
                title = "Player Info"
                field("ID", "`${user.snowflake}`")
                field("Last Name", "`${user.lastName}`")
                field("Names", details.names.joinToString(transform = { "`$it`" }))
                field("First Join", renderer.renderInstant(user.snowflake.timestamp))
                field("Last Join", renderer.renderInstant(user.lastJoin))
                field("Times Joined", user.timesJoined.toString())
                if (discord.isAllowed(actor.member.user, Rank.ADMIN)) {
                    field("Uuid", "`${user.uuid}`")
                    field("Last Address", "`${user.lastAddress.hostAddress}`")
                    field(
                        "Addresses",
                        details.addresses.joinToString(transform = { "`${it.hostAddress}`" }))
                }
            })
    }

    @Command(["player", "search"])
    @NonEphemeral
    suspend fun onPlayerSearch(actor: InteractionSender.Slash, query: String) {
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
                    "- ${it.lastName.stripMindustryColors()} / `${it.snowflake}`"
                }
            }
        if (hasMore) {
            text += "\n\nAnd more..."
        }

        actor.respond(
            Embed {
                title = "Player Search"
                description = text
            })
    }
}
