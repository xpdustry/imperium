// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.commands

import com.xpdustry.imperium.backend.misc.Embed
import com.xpdustry.imperium.backend.misc.await
import com.xpdustry.imperium.backend.service.DiscordService
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.PlayerIDLike
import com.xpdustry.imperium.common.user.UserManager
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

@Inject
class PlayerCommand(
    private val users: UserManager,
    private val discord: DiscordService,
    private val renderer: TimeRenderer,
    private val codec: IdentifierCodec,
) : ImperiumApplication.Listener {

    @ImperiumCommand(["player", "info"])
    suspend fun onPlayerInfoCommand(interaction: SlashCommandInteraction, player: PlayerIDLike) {
        val reply = interaction.deferReply(true).await()
        val user = users.findById(player.id)!!
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
