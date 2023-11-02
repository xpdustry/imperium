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

import com.google.common.cache.CacheBuilder
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.common.security.VerificationMessage
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.service.DiscordService
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import org.bson.types.ObjectId

class VerifyCommand(instances: InstanceManager) : ImperiumApplication.Listener {

    private val accounts = instances.get<AccountManager>()
    private val api = instances.get<DiscordService>()
    private val limiter = SimpleRateLimiter<Long>(3, 10.minutes)
    private val messenger = instances.get<Messenger>()
    private val pending =
        CacheBuilder.newBuilder()
            .expireAfterWrite(10.minutes.toJavaDuration())
            .build<Int, Pair<ObjectId, MindustryUUID>>()

    override fun onImperiumInit() {
        messenger.subscribe<VerificationMessage> { message ->
            if (message.response) return@subscribe
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

        val bound = accounts.findByDiscordId(actor.user.id)
        if (bound != null) {
            actor.respond(
                "Your discord account is already bound to the cn account ${bound.username}.")
            return
        }

        accounts.updateById(data.first) { it.discord = actor.user.id }
        api.syncRoles(actor.user, data.first)
        messenger.publish(VerificationMessage(data.first, data.second, code, true))
        actor.respond("You have been verified!")
    }
}