// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.toInetAddressOrNull
import com.xpdustry.imperium.common.security.AddressWhitelist
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.await
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

@Inject
class WhitelistCommand constructor(private val whitelist: AddressWhitelist) : ImperiumApplication.Listener {

    @ImperiumCommand(["whitelist", "add"], Rank.ADMIN)
    suspend fun onWhitelistAddCommand(interaction: SlashCommandInteraction, address: String, reason: String) {
        val reply = interaction.deferReply(true).await()
        val ip = address.toInetAddressOrNull()
        if (ip == null) {
            reply.sendMessage("The ip address is not valid.").await()
        } else {
            whitelist.addAddress(ip, reason)
            reply.sendMessage("Added address to whitelist.").await()
        }
    }

    @ImperiumCommand(["whitelist", "remove"], Rank.ADMIN)
    suspend fun onWhitelistRemoveCommand(interaction: SlashCommandInteraction, address: String) {
        val reply = interaction.deferReply(true).await()
        val ip = address.toInetAddressOrNull()
        if (ip == null) {
            reply.sendMessage("The ip address is not valid.").await()
        } else if (whitelist.containsAddress(ip)) {
            whitelist.removeAddress(ip)
            reply.sendMessage("Removed address from whitelist.").await()
        } else {
            reply.sendMessage("The whitelist does not contain this address.").await()
        }
    }

    @ImperiumCommand(["whitelist", "list"], Rank.ADMIN)
    suspend fun onWhitelistAddCommand(interaction: SlashCommandInteraction) {
        val reply = interaction.deferReply(true).await()
        reply
            .sendMessageEmbeds(
                // TODO: That shit will crash beyond 125 entries
                whitelist.listAdresses().chunked(25).map { entries ->
                    Embed {
                        if (entries.isEmpty()) {
                            description = "none"
                        } else {
                            for ((address, reason) in entries) {
                                field {
                                    name = address.hostAddress
                                    value = reason
                                    inline = false
                                }
                            }
                        }
                    }
                }
            )
            .await()
    }
}
