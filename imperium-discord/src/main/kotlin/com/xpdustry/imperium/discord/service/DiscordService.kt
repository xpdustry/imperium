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
import com.xpdustry.imperium.discord.misc.toSnowflake
import discord4j.core.DiscordClient
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.Category
import reactor.core.publisher.Mono

interface DiscordService {
    val gateway: GatewayDiscordClient
    fun getMainGuild(): Mono<Guild>
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

        if (getMainGuild().blockOptional().isEmpty) {
            throw IllegalStateException("Main guild not found")
        }

        // TODO Do config validation elsewhere ?
        val liveChat = config.discord.categories.liveChat?.toSnowflake()
        if (liveChat != null) {
            val channel = gateway.getChannelById(liveChat).onErrorComplete().blockOptional()
            if (channel.isEmpty) {
                throw IllegalStateException("Live chat channel not found")
            }
            if (channel.get() !is Category) {
                throw IllegalStateException("Live chat channel is not a category")
            }
        }
    }

    override fun getMainGuild(): Mono<Guild> = gateway.guilds.single()

    override fun onImperiumExit() {
        gateway.logout().block()
    }
}
