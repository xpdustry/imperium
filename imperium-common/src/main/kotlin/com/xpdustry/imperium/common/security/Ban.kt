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

import com.xpdustry.imperium.common.mongo.Entity
import com.xpdustry.imperium.common.mongo.timestamp
import org.bson.types.ObjectId
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

data class Ban(
    override val _id: ObjectId = ObjectId(),
    var target: InetAddress,
    var reason: Reason,
    var details: String? = null,
    var duration: Duration? = Duration.ofDays(1L),
    var pardoned: Boolean = false,
) : Entity<ObjectId> {
    val expired: Boolean get() = expiration < Instant.now()
    val expiration: Instant get() = duration?.let { timestamp.plus(it) } ?: Instant.MAX
    val permanent: Boolean get() = duration == null

    enum class Reason {
        GRIEFING,
        TOXICITY,
        CHEATING,
        SPAMMING,
        SABOTAGE,
        NSFW,
        OTHER,
    }
}
