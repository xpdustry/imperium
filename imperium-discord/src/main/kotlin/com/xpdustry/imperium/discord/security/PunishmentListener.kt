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
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.MindustryUUIDAsLong
import com.xpdustry.imperium.common.misc.toCRC32Muuid
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.PunishmentMessage
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.service.DiscordService
import java.awt.Color
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel

class PunishmentListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Discord>()
    private val discord = instances.get<DiscordService>()
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val messenger = instances.get<Messenger>()
    private val renderer = instances.get<TimeRenderer>()

    override fun onImperiumInit() {
        messenger.consumer<PunishmentMessage> { (author, type, snowflake, server) ->
            val punishment = punishments.findBySnowflake(snowflake)!!
            val user = punishment.target.let { users.findBySnowflake(it) }!!
            (getNotificationChannel() ?: return@consumer).sendMessageEmbeds(
                Embed {
                    author {
                        when (author) {
                            is Identity.Mindustry -> name = author.name
                            is Identity.Server -> name = "AUTO-MOD"
                            is Identity.Discord -> {
                                val member = discord.getMainServer().getMemberById(author.id)
                                if (member == null) {
                                    name = "unknown"
                                } else {
                                    name = member.effectiveName
                                    iconUrl = member.avatarUrl
                                }
                            }
                        }
                    }

                    // TODO Move embed creation to a single method for "/punishment info"
                    when (type) {
                        PunishmentMessage.Type.CREATE -> {
                            color = Color.RED.rgb
                            title = "Punishment"
                            field("Target", user.lastName)
                            field("Type", punishment.type.toString())
                            field("Duration", renderer.renderDuration(punishment.duration))
                            if (server != config.name) field("Server", server)
                            field("Reason", punishment.reason, false)

                            when (val metadata = punishment.metadata) {
                                is Punishment.Metadata.None -> Unit
                                is Punishment.Metadata.Votekick -> {
                                    field("Votes for", renderPlayerList(metadata.yes))
                                    field("Votes against", renderPlayerList(metadata.nay))
                                }
                            }
                        }
                        PunishmentMessage.Type.PARDON -> {
                            color = Color.GREEN.rgb
                            title = "Pardon"
                            field("Target", user.lastName)
                            field("Type", punishment.type.toString())
                            field("Reason", punishment.pardon?.reason ?: "`<UNKNOWN>`", false)
                        }
                    }

                    footer("ID: ${punishment.snowflake}")
                })
        }
    }

    private suspend fun renderPlayerList(players: Iterable<MindustryUUIDAsLong>) = buildString {
        for (player in players) {
            val user = users.findByUuid(player.toCRC32Muuid())
            if (user == null) {
                appendLine("- unknown")
            } else {
                appendLine("- ${user.lastName} / `${user.snowflake}`")
            }
        }
    }

    private fun getNotificationChannel(): TextChannel? {
        val channel = discord.getMainServer().getTextChannelById(config.channels.notifications)
        if (channel == null) {
            LOGGER.error("Could not find notifications channel")
        }
        return channel
    }

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}
