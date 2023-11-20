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

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.security.permission.Permission
import java.util.concurrent.TimeUnit
import org.javacord.api.DiscordApi
import org.javacord.api.DiscordApiBuilder
import org.javacord.api.entity.intent.Intent
import org.javacord.api.entity.server.Server
import org.javacord.api.entity.user.User

interface DiscordService {
    val api: DiscordApi

    fun getMainServer(): Server

    fun isAllowed(user: User, permission: Permission): Boolean
}

class SimpleDiscordService(
    private val config: ServerConfig.Discord,
    private val accounts: AccountManager
) : DiscordService, ImperiumApplication.Listener {
    override lateinit var api: DiscordApi

    override fun onImperiumInit() {
        api =
            DiscordApiBuilder()
                .setToken(config.token.value)
                .setUserCacheEnabled(true)
                .addIntents(
                    Intent.MESSAGE_CONTENT,
                    Intent.GUILDS,
                    Intent.GUILD_MEMBERS,
                    Intent.GUILD_MESSAGES,
                    Intent.GUILD_MESSAGE_REACTIONS,
                    Intent.DIRECT_MESSAGES,
                )
                .login()
                .orTimeout(15L, TimeUnit.SECONDS)
                .join()
    }

    override fun getMainServer(): Server = api.servers.first()

    override fun isAllowed(user: User, permission: Permission): Boolean {
        if (permission == Permission.EVERYONE) {
            return true
        }
        return getMainServer().getRoles(user).any {
            config.roles2permissions[it.id]?.contains(permission) == true
        }
    }

    override fun onImperiumExit() {
        api.disconnect().orTimeout(15L, TimeUnit.SECONDS).join()
    }
}
