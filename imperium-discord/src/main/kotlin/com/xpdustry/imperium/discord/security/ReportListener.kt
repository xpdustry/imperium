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
package com.xpdustry.imperium.discord.security

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.security.ReportMessage
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.discord.service.DiscordService
import java.awt.Color
import kotlinx.coroutines.future.await
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.message.embed.EmbedBuilder

// TODO Sanitize player names... Mmmh... Sanitize strings in general with an extension function?
class ReportListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val messenger = instances.get<Messenger>()
    private val config = instances.get<ServerConfig.Discord>()
    private val users = instances.get<UserManager>()

    override fun onImperiumInit() {
        messenger.consumer<ReportMessage> { report ->
            // TODO Add quick action buttons ?
            getReportChannel()
                .sendMessage(
                    EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("Report from ${report.serverName}")
                        .addField(
                            "Sender",
                            "${report.sender.name} / `${users.getByIdentity(report.sender).snowflake}`",
                            true)
                        .addField(
                            "Target",
                            "${report.target.name} / `${users.getByIdentity(report.target).snowflake}`",
                            true)
                        .addField(
                            "Reason",
                            "${report.reason.name.lowercase().capitalize()} (${report.details ?: "No detail"})",
                            false),
                )
                .await()
        }
    }

    private fun getReportChannel(): ServerTextChannel =
        discord.getMainServer().getTextChannelById(config.channels.reports).orElseThrow {
            RuntimeException("The report channel is not found")
        }
}
