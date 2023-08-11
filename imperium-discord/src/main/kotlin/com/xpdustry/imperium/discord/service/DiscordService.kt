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
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.misc.switchIfEmpty
import com.xpdustry.imperium.common.misc.toErrorMono
import discord4j.core.DiscordClient
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import reactor.core.publisher.Mono

interface DiscordService {
    val gateway: GatewayDiscordClient
    fun getMainGuild(): Mono<Guild>
}

class SimpleDiscordService(private val config: ServerConfig.Discord) : DiscordService, ImperiumApplication.Listener {
    override lateinit var gateway: GatewayDiscordClient

    override fun onImperiumInit() {
        gateway = DiscordClient.builder(config.token.value)
            .build()
            .gateway()
            .login()
            .block()!!
    }

    override fun getMainGuild(): Mono<Guild> = gateway.guilds.next().switchIfEmpty {
        IllegalStateException("Main guild not found").toErrorMono()
    }

    override fun onImperiumExit() {
        gateway.logout().block()
    }
}
