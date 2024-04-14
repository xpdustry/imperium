/*
 * Imperium, the software collection powering the Xpdustry network.
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
 *
 */
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.MindustryUSID
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
        buildCache<Int, Verification> { expireAfterWrite(10.minutes.toJavaDuration()) }

    override fun onImperiumInit() {
        messenger.consumer<VerificationMessage> { message ->
            if (message.response) return@consumer
            pending.put(message.code, Verification(message.account, message.uuid, message.usid))
        }
    }

    @ImperiumCommand(["verify"])
    @NonEphemeral
    private suspend fun onVerifyCommand(actor: InteractionSender.Slash, code: Int) {
        if (!limiter.incrementAndCheck(actor.member.idLong)) {
            actor.respond("You made too many attempts! Wait 10 minutes and try again.")
            return
        }

        val verification = pending.getIfPresent(code)
        if (verification == null) {
            actor.respond("Invalid code.")
            return
        }

        val bound = accounts.findByDiscord(actor.member.idLong)
        if (bound != null) {
            actor.respond(
                "Your discord account is already bound to the cn account ${bound.username}.")
            return
        }

        when (accounts.updateDiscord(verification.account, actor.member.idLong)) {
            is AccountResult.Success -> Unit
            else -> {
                actor.respond("An unexpected error occurred while verifying you.")
                logger.error("Failed to update user discord ${actor.member.effectiveName}")
            }
        }

        var rank = accounts.findBySnowflake(verification.account)!!.rank
        for (role in actor.member.roles) {
            rank = maxOf(rank, config.roles2ranks[role.idLong] ?: Rank.VERIFIED)
        }
        accounts.setRank(verification.account, rank)

        discord.syncRoles(actor.member)
        messenger.publish(
            VerificationMessage(
                verification.account, verification.uuid, verification.usid, code, true))
        actor.respond("You have been verified!")
    }

    data class Verification(
        val account: Snowflake,
        val uuid: MindustryUUID,
        val usid: MindustryUSID,
    )

    companion object {
        private val logger by LoggerDelegate()
    }
}
