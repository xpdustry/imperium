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
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
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
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

class VerifyCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val discord = instances.get<DiscordService>()
    private val accounts = instances.get<AccountManager>()
    private val limiter = SimpleRateLimiter<Long>(3, 10.minutes)
    private val messenger = instances.get<Messenger>()
    private val pending = buildCache<Int, Verification> { expireAfterWrite(10.minutes.toJavaDuration()) }

    override fun onImperiumInit() {
        messenger.consumer<VerificationMessage> { message ->
            if (message.response) return@consumer
            pending.put(message.code, Verification(message.account, message.uuid, message.usid))
        }
    }

    @ImperiumCommand(["verify"])
    suspend fun onVerifyCommand(interaction: SlashCommandInteraction, code: Int) {
        val reply = interaction.deferReply(false).await()
        if (!limiter.incrementAndCheck(interaction.user.idLong)) {
            reply.sendMessage("You made too many attempts! Wait 10 minutes and try again.").await()
            return
        }

        val verification = pending.getIfPresent(code)
        if (verification == null) {
            reply.sendMessage("Invalid code.").await()
            return
        }

        if (!accounts.updateDiscord(verification.account, interaction.user.idLong)) {
            reply.sendMessage("An unexpected error occurred while verifying you.").await()
            logger.error("Failed to update user discord ${interaction.user.effectiveName}")
            return
        }

        var rank = accounts.selectById(verification.account)!!.rank
        for (role in interaction.member!!.roles) {
            rank = maxOf(rank, config.discord.roles2ranks[role.idLong] ?: Rank.VERIFIED)
        }
        accounts.updateRank(verification.account, rank)

        discord.syncRoles(interaction.member!!)
        messenger.publish(VerificationMessage(verification.account, verification.uuid, verification.usid, code, true))
        reply.sendMessage("You have been verified!").await()
    }

    data class Verification(val account: Int, val uuid: MindustryUUID, val usid: MindustryUSID)

    companion object {
        private val logger by LoggerDelegate()
    }
}
