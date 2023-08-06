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
package com.xpdustry.imperium.discord.service

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.misc.switchIfEmpty
import com.xpdustry.imperium.common.misc.toValueMono
import com.xpdustry.imperium.discord.misc.toSnowflake
import discord4j.core.DiscordClient
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.channel.Category
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.spec.TextChannelCreateSpec
import discord4j.rest.util.Permission
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

interface DiscordService {
    val gateway: GatewayDiscordClient
    fun getMainGuild(): Mono<Guild>
    fun getNotificationChannel(): Mono<TextChannel>
}

class SimpleDiscordService(private val config: ImperiumConfig) : DiscordService, ImperiumApplication.Listener {
    override lateinit var gateway: GatewayDiscordClient

    override fun onImperiumInit() {
        if (config.discord.token == null) {
            throw IllegalStateException("Discord token not set")
        }
        gateway = DiscordClient.builder(config.discord.token!!.value)
            .build()
            .gateway()
            .login()
            .block()!!

        val guild = getMainGuild().block()
            ?: throw IllegalStateException("Main guild not found")

        if (guild.selfMember.flatMap(Member::getBasePermissions).map { it.contains(Permission.ADMINISTRATOR) }.block() != true) {
            throw IllegalStateException("Bot must have administrator permissions")
        }

        // TODO Do config validation elsewhere ?
        checkChannel(config.discord.categories.liveChat, Category::class)
        checkChannel(config.discord.channels.notifications, TextChannel::class)
    }

    private fun checkChannel(snowflake: Long?, klass: KClass<out GuildChannel>) {
        if (snowflake != null) {
            val channel = gateway.getChannelById(snowflake.toSnowflake()).onErrorComplete().blockOptional()
            if (channel.isEmpty) {
                throw IllegalStateException("Channel $channel not found")
            }
            if (!klass.isInstance(channel.get())) {
                throw IllegalStateException("Channel $channel is not a ${klass.simpleName}")
            }
        }
    }

    override fun getMainGuild(): Mono<Guild> = gateway.guilds.single()

    override fun getNotificationChannel(): Mono<TextChannel> = config.discord.channels.notifications
        .toValueMono()
        .flatMap { snowflake ->
            getMainGuild().flatMap {
                it.getChannelById(snowflake.toSnowflake()).cast(TextChannel::class.java)
            }
        }
        .switchIfEmpty {
            getMainGuild().flatMap { guild ->
                guild.channels.filter { it is TextChannel && it.name.lowercase() == "notifications" }
                    .next()
                    .cast(TextChannel::class.java)
                    .switchIfEmpty {
                        guild.createTextChannel(TextChannelCreateSpec.of("Notifications"))
                    }
            }
        }

    override fun onImperiumExit() {
        gateway.logout().block()
    }
}
