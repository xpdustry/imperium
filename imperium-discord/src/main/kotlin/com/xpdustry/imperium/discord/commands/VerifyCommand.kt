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
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.buildCache
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.common.security.VerificationMessage
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.service.DiscordService
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class VerifyCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Discord>()
    private val discord = instances.get<DiscordService>()
    private val accounts = instances.get<AccountManager>()
    private val limiter = SimpleRateLimiter<Long>(3, 10.minutes)
    private val messenger = instances.get<Messenger>()
    private val pending =
        buildCache<Int, Pair<Snowflake, MindustryUUID>> {
            expireAfterWrite(10.minutes.toJavaDuration())
        }

    override fun onImperiumInit() {
        messenger.consumer<VerificationMessage> { message ->
            if (message.response) return@consumer
            pending.put(message.code, message.account to message.uuid)
        }
    }

    @Command(["verify"])
    @NonEphemeral
    private suspend fun onVerifyCommand(actor: InteractionSender, code: Int) {
        if (!limiter.incrementAndCheck(actor.user.id)) {
            actor.respond("You made too many attempts! Wait 10 minutes and try again.")
            return
        }

        val data = pending.getIfPresent(code)
        if (data == null) {
            actor.respond("Invalid code.")
            return
        }

        val bound = accounts.findByDiscord(actor.user.id)
        if (bound != null) {
            actor.respond(
                "Your discord account is already bound to the cn account ${bound.username}.")
            return
        }

        when (accounts.updateDiscord(data.first, actor.user.id)) {
            is AccountResult.Success -> Unit
            else -> {
                actor.respond("An unexpected error occurred while verifying you.")
                logger.error("Failed to update user discord ${actor.user.name}")
            }
        }

        var rank = accounts.findBySnowflake(data.first)!!.rank
        for (role in actor.user.getRoles(discord.getMainServer())) {
            rank = maxOf(rank, config.roles2ranks[role.id] ?: Rank.VERIFIED)
        }
        accounts.setRank(data.first, rank)

        discord.syncRoles(actor.user)
        messenger.publish(VerificationMessage(data.first, data.second, code, true))
        actor.respond("You have been verified!")
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
