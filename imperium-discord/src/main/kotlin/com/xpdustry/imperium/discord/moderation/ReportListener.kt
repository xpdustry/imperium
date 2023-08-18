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
package com.xpdustry.imperium.discord.moderation

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.misc.switchIfEmpty
import com.xpdustry.imperium.common.misc.toErrorMono
import com.xpdustry.imperium.common.moderation.ReportMessage
import com.xpdustry.imperium.discord.misc.toSnowflake
import com.xpdustry.imperium.discord.service.DiscordService
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import reactor.core.publisher.Mono

// TODO Sanitize player names... Mmmh... Sanitize strings in general with an extension function?
class ReportListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val messenger = instances.get<Messenger>()
    private val config = instances.get<ServerConfig.Discord>()

    override fun onImperiumInit() {
        messenger.subscribe<ReportMessage> { report ->
            getNotificationChannel().flatMap { channel ->
                channel.createMessage(
                    EmbedCreateSpec.builder()
                        .title("Report from ${report.serverName}")
                        .addField(
                            EmbedCreateFields.Field.of(
                                "Sender",
                                "${report.sender.name} / `${report.sender.uuid}`",
                                false,
                            ),
                        )
                        .addField(
                            EmbedCreateFields.Field.of(
                                "Target",
                                "${report.target.name} / `${report.target.uuid}`",
                                false,
                            ),
                        )
                        .addField(
                            EmbedCreateFields.Field.of(
                                "Reason",
                                "${report.reason.name.lowercase().capitalize()} (${report.detail ?: "No detail"})",
                                false,
                            ),
                        )
                        .build(),
                )
            }
                .subscribe()
        }
    }

    private fun getNotificationChannel(): Mono<TextChannel> = discord.getMainGuild().flatMap { guild ->
        guild.channels.filter { it is TextChannel && it.id == config.channels.notifications.toSnowflake() }
            .next()
            .cast(TextChannel::class.java)
            .switchIfEmpty { RuntimeException("The notifications channel is not found").toErrorMono() }
    }
}
