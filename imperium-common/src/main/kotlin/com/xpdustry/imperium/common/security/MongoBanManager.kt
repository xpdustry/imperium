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
package com.xpdustry.imperium.common.security

import com.mongodb.client.model.Filters
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.mongo.MongoEntityCollection
import com.xpdustry.imperium.common.mongo.MongoProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import org.bson.types.ObjectId
import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

internal class MongoBanManager(private val mongo: MongoProvider, private val messenger: Messenger) : BanManager, ImperiumApplication.Listener {

    private lateinit var bans: MongoEntityCollection<Ban, ObjectId>

    override fun onImperiumInit() {
        bans = mongo.getCollection("punishments", Ban::class)
    }

    override suspend fun punish(sender: Identity?, target: InetAddress, reason: Ban.Reason, details: String?, duration: Duration?) {
        val jDuration = if (duration == null) {
            null
        } else {
            maxOf(duration, getBanDuration(findAllByTarget(target).count())).toJavaDuration()
        }
        val ban = Ban(target = target, reason = reason, details = details, duration = jDuration)
        bans.save(ban)
        messenger.publish(BanMessage(sender, ban._id))
    }

    override suspend fun findById(id: ObjectId): Ban? =
        bans.findById(id)

    override suspend fun findAllByTarget(target: InetAddress): Flow<Ban> =
        bans.find(Filters.eq("target", target))

    private fun getBanDuration(count: Int): Duration {
        return when (count) {
            0 -> 1.days
            1 -> 3.days
            2 -> 7.days
            3 -> 14.days
            else -> (30 * (count - 3)).days
        }
    }
}
