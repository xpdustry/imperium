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
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.PunishmentMessage
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.discord.service.DiscordService
import java.awt.Color
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.future.await
import org.javacord.api.entity.message.embed.EmbedBuilder

class PunishmentListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Discord>()
    private val discord = instances.get<DiscordService>()
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val messenger = instances.get<Messenger>()
    private val renderer = instances.get<TimeRenderer>()

    override fun onImperiumInit() {
        messenger.consumer<PunishmentMessage> { (author, type, snowflake) ->
            val punishment = punishments.findBySnowflake(snowflake)!!
            val user = punishment.target.uuid?.let { users.findByUuid(it) }
            val embed =
                EmbedBuilder().apply {
                    when (type) {
                        PunishmentMessage.Type.CREATE -> {
                            setColor(Color.RED)
                            setTitle("Punishment ${punishment.snowflake}")
                            addField("Target", user?.lastName ?: "`<UNKNOWN>`", true)
                            addField("Type", punishment.type.toString(), true)
                            addField("Duration", renderer.renderDuration(punishment.duration), true)
                            addField("Reason", punishment.reason, false)
                        }
                        PunishmentMessage.Type.PARDON -> {
                            setColor(Color.GREEN)
                            setTitle("Pardoned ${punishment.snowflake}")
                            addField("Target", user?.lastName ?: "`<UNKNOWN>`", true)
                            addField("Type", punishment.type.toString(), true)
                            addField("Reason", punishment.pardon?.reason ?: "`<UNKNOWN>`", false)
                        }
                    }
                }

            when (author) {
                is Identity.Discord -> embed.setAuthor(discord.api.getUserById(author.id).await())
                is Identity.Mindustry -> embed.setAuthor(author.name)
                is Identity.Server -> embed.setAuthor("SYSTEM")
            }

            val channel =
                discord
                    .getMainServer()
                    .getTextChannelById(config.channels.notifications)
                    .getOrNull()
            if (channel == null) {
                logger.error("Could not find notifications channel")
                return@consumer
            }

            channel.sendMessage(embed)
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
