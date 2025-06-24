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
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

class PlayerCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val discord = instances.get<DiscordService>()
    private val renderer = instances.get<TimeRenderer>()
    private val codec = instances.get<IdentifierCodec>()

    @ImperiumCommand(["player", "info"])
    suspend fun onPlayerInfoCommand(interaction: SlashCommandInteraction, player: String) {
        val reply = interaction.deferReply(true).await()
        // TODO THIS IS GOOFY, REPLACE WITH A PROPER PARSER
        var user = users.findByUuid(player)
        if (user == null) {
            user = codec.tryDecode(player)?.let { users.findById(it) }
        }
        if (user == null) {
            reply.sendMessage("Player not found.").await()
            return
        }
        val details = users.findNamesAndAddressesById(user.id)
        reply
            .sendMessageEmbeds(
                Embed {
                    title = "Player Info"
                    field("ID", "`${codec.encode(user.id)}`")
                    field("Last Name", "`${user.lastName}`")
                    field("Names", details.names.joinToString(transform = { "`$it`" }))
                    field("First Join", renderer.renderInstant(user.firstJoin))
                    field("Last Join", renderer.renderInstant(user.lastJoin))
                    field("Times Joined", user.timesJoined.toString())
                    if (discord.isAllowed(interaction.user, Rank.ADMIN)) {
                        field("Uuid", "`${user.uuid}`")
                        field("Last Address", "`${user.lastAddress.hostAddress}`")
                        field("Addresses", details.addresses.joinToString(transform = { "`${it.hostAddress}`" }))
                    }
                }
            )
            .await()
    }

    @ImperiumCommand(["player", "search"])
    suspend fun onPlayerSearch(interaction: SlashCommandInteraction, query: String) {
        val reply = interaction.deferReply(true).await()
        val users = users.searchUserByName(query).take(21).toCollection(mutableListOf())
        if (users.isEmpty()) {
            reply.sendMessage("No players found.").await()
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
                    "- ${it.lastName.stripMindustryColors()} / `${codec.encode(it.id)}`"
                }
            }
        if (hasMore) {
            text += "\n\nAnd more..."
        }

        reply
            .sendMessageEmbeds(
                Embed {
                    title = "Player Search"
                    description = text
                }
            )
            .await()
    }
}
