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
package com.xpdustry.imperium.discord.security

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.DiscordConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.security.ReportMessage
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.discord.command.ButtonCommand
import com.xpdustry.imperium.discord.misc.MessageCreate
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import java.awt.Color
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button

// TODO Sanitize player names... Mmmh... Sanitize strings in general with an extension function? 
// Why this note in this file? Should be in the message filter.
class ReportListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val messenger = instances.get<Messenger>()
    private val config = instances.get<DiscordConfig>()
    private val users = instances.get<UserManager>()

    override fun onImperiumInit() {
        messenger.consumer<ReportMessage> { report ->
            // TODO Add quick action buttons ?
            // Yes
            val message = (getReportChannel() ?: return@consumer)
                .sendMessage(
                    MessageCreate {
                        embeds += Embed {
                            color = Color.YELLOW.rgb
                            title = "Report from ${report.serverName}"

                            field {
                            name = "Sender"
                            value =
                                "${report.sender.name} / `${users.getByIdentity(report.sender).snowflake}`"
                            }

                            
                            field {
                            name = "Target"
                            value =
                                "${report.target.name} / `${users.getByIdentity(report.target).snowflake}`"
                            }

                            field {
                            name = "Reason"
                            value =
                                "${report.reason.name.lowercase().capitalize()} (${report.details ?: "No detail"})"
                            inline = false
                            }
                        }
                        components +=
                            ActionRow.of(
                                Button.danger(REPORT_BAN_BUTTON, "Ban")
                                    .withEmoji(ImperiumEmojis.HAMMER),
                                Button.primary(REPORT_REVIEW_BUTTON, "Review")
                                    .withEmoji(ImperiumEmojis.MAGNIFYING_GLASS),
                                Button.primary(REPORT_HISTORY_BUTTON, "History")
                                    .withEmoji(ImperiumEmojis.NOTEPAD),
                                Button.secondary(REPORT_CLOSE_BUTTON, "Close")
                                    .withEmoji(ImperiumEmojis.CROSS_MARK),
                            )
                    })
                .await()

            message
            .createThreadChannel("Report on ${report.target.name}")
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS)
            .await()
        }
    }

    private fun getReportChannel(): TextChannel? {
        val channel = discord.getMainServer().getTextChannelById(config.channels.reports)
        if (channel == null) {
            LOGGER.error("The report channel is not defined found.")
        }
        return channel
    }

    @ButtonCommand(REPORT_BAN_BUTTON, Rank.MODERATOR)
    private suspend fun onBanButtonCommand(actor: InteractionSender.Button) {
        // val target = report.target
        // TODO make the form
        actor.respond("Ban button is wip")
        updateReportEmbed(actor, Color(155, 255, 255), "ban")
    }

    @ButtonCommand(REPORT_REVIEW_BUTTON, Rank.MODERATOR)
    private suspend fun onReviewButtonCommand(actor: InteractionSender.Button) {
        updateReportEmbed(actor, Color.RED, "review")
        actor.respond("Marked report as under-review by ${actor.member.asMention}")
    }

    @ButtonCommand(REPORT_HISTORY_BUTTON, Rank.MODERATOR)
    private suspend fun onHistoryButtonCommand(actor: InteractionSender.Button) {
        // presentHistory(report.target)
        // TODO: Make the history response 
        actor.respond("History button is wip") // change to history response
    }

    private suspend fun updateReportEmbed(
        actor: InteractionSender.Button,
        color: Color,
        type: String,
        snowflake: Snowflake? = null
    ) {
        if (type == "review") {
            val clickedButton = actor.componentId
            val actionRows = actor.message.actionRows
            val newActionRows = actionRows.map { actionRow -> 
                val newComponents = actionRow.components.filterNot { component ->
                    component is Button && component.id == clickedButton
                }
            actionRow.withComponents(newComponents)
            }}

        if (type == "ban" || type == "close") {
            val newActionRows = emptyList<ActionRow>()
            }

        actor.message
            .editMessageEmbeds(
                Embed(actor.message.embeds.first()) {
                    this@Embed.color = color.rgb
                    if (type =="review") { field("Reviewer", actor.member.asMention, false)}
                    if (type == "ban") { field("Result", "Player ${report.target.name} was banned by ${actor.member.asMention}", false)}
                    if (type == "close") { field("Result", "Report closed by ${actor.member.asMention}", false)}
                })
            .setActionRows(newActionRows)
    }

    companion object {
        private val LOGGER = logger<ReportListener>()
    }
}
