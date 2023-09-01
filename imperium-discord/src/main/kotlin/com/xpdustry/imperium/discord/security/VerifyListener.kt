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

import com.google.common.cache.CacheBuilder
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.MindustryUUID
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.security.RateLimiter
import com.xpdustry.imperium.common.security.VerificationMessage
import com.xpdustry.imperium.discord.interaction.InteractionActor
import com.xpdustry.imperium.discord.interaction.command.Command
import org.bson.types.ObjectId
import java.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class VerifyListener(instances: InstanceManager) : ImperiumApplication.Listener {
    // TODO There is an issue with the RateLimiter, the real limit is 3
    private val limiter = RateLimiter<Long>(2, Duration.ofMinutes(10))
    private val messenger = instances.get<Messenger>()
    private val pending = CacheBuilder.newBuilder()
        .expireAfterWrite(10.minutes.toJavaDuration())
        .build<Int, Pair<ObjectId, MindustryUUID>>()

    override fun onImperiumInit() {
        messenger.subscribe<VerificationMessage> { message ->
            if (message.response) return@subscribe
            pending.put(message.code, message.account to message.uuid)
        }
    }

    @Command("verify", ephemeral = false)
    private suspend fun onVerifyCommand(actor: InteractionActor, code: Int) {
        if (!limiter.check(actor.user.id)) {
            actor.respond("You made too many attempts! Wait 10 minutes and try again.")
            return
        }

        val data = pending.getIfPresent(code)
        if (data == null) {
            actor.respond("Invalid code.")
            limiter.increment(actor.user.id)
            return
        }

        messenger.publish(VerificationMessage(data.first, data.second, code, true))
        actor.respond("You have been verified!")
    }
}
