// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.security

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.security.ReportMessage
import com.xpdustry.imperium.discord.command.MenuCommand
import com.xpdustry.imperium.discord.commands.getPlayerHistory
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.ImperiumEmojis
import com.xpdustry.imperium.discord.misc.MessageCreate
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.misc.disableComponents
import com.xpdustry.imperium.discord.service.DiscordService
import java.awt.Color
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction

class ReportListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val messenger = instances.get<MessageService>()
    private val config = instances.get<ImperiumConfig>()
    private val codec = instances.get<IdentifierCodec>()

    override fun onImperiumInit() {
        messenger.subscribe<ReportMessage> { report ->
            val thread =
                (getReportChannel() ?: return@subscribe)
                    .sendMessage(
                        MessageCreate {
                            val moderator = config.discord.alertsRole?.let { discord.getMainServer().getRoleById(it) }
                            if (moderator != null) {
                                content += moderator.asMention
                            }

                            embeds += Embed {
                                color = Color.YELLOW.rgb

                                field {
                                    name = "Sender"
                                    value = report.senderName
                                }

                                field {
                                    name = "ID"
                                    value = codec.encode(report.senderId)
                                }

                                field {}

                                field {
                                    name = "Target"
                                    value = report.targetName
                                }

                                field {
                                    name = "ID"
                                    value = codec.encode(report.targetId)
                                }

                                field {}

                                field {
                                    name = "Server"
                                    value = report.serverName
                                    inline = false
                                }

                                field {
                                    name = "Reason"
                                    value = report.reason.name.lowercase().capitalize()
                                    inline = false
                                }
                            }

                            components +=
                                ActionRow.of(
                                    Button.primary(REPORT_REVIEW_BUTTON, "Review")
                                        .withEmoji(ImperiumEmojis.MAGNIFYING_GLASS),
                                    Button.secondary(REPORT_IGNORE_BUTTON, "Ignore")
                                        .withEmoji(ImperiumEmojis.CROSS_MARK),
                                )
                        }
                    )
                    .await()
                    .createThreadChannel("Report on ${report.targetName}")
                    .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS)
                    .await()
            getPlayerHistory(messenger, report.serverName, report.targetId)?.lineSequence()?.chunked(10)?.forEach {
                thread.sendMessage("```\n${it.joinToString("\n")}\n```").await()
            }
        }
    }

    private fun getReportChannel(): TextChannel? {
        val channel = discord.getMainServer().getTextChannelById(config.discord.channels.reports)
        if (channel == null) {
            LOGGER.error("The report channel is not defined found.")
        }
        return channel
    }

    @MenuCommand(REPORT_REVIEW_BUTTON, Rank.MODERATOR)
    suspend fun onReviewButton(interaction: ButtonInteraction) {
        val reply = interaction.deferReply(true).await()
        interaction.message
            .editMessageEmbeds(
                Embed(interaction.message.embeds.first()) {
                    field {
                        name = "Assigned to"
                        value = interaction.member!!.asMention
                        inline = false
                    }
                }
            )
            .await()
        interaction.message.disableComponents()
        reply.sendMessage("You have claimed this report, good luck!").await()
    }

    @MenuCommand(REPORT_IGNORE_BUTTON, Rank.MODERATOR)
    suspend fun onIgnoreButton(interaction: ButtonInteraction) {
        val reply = interaction.deferReply(true).await()
        interaction.message
            .editMessageEmbeds(
                Embed(interaction.message.embeds.first()) {
                    field {
                        name = "Ignored by"
                        value = interaction.member!!.asMention
                        inline = false
                    }
                }
            )
            .await()
        interaction.message.disableComponents()
        reply.sendMessage("You have ignored this report.").await()
    }

    companion object {
        private const val REPORT_REVIEW_BUTTON = "report-review:1"
        private const val REPORT_IGNORE_BUTTON = "report-ignore:1"
        private val LOGGER = logger<ReportListener>()
    }
}
