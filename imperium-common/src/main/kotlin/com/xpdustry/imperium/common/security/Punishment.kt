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

import com.xpdustry.imperium.common.account.MindustryUUID
import com.xpdustry.imperium.common.database.Entity
import com.xpdustry.imperium.common.database.timestamp
import org.bson.types.ObjectId
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

data class Punishment(
    var targetAddress: InetAddress,
    var targetUuid: MindustryUUID? = null,
    override val _id: ObjectId = ObjectId(),
    var reason: String = "Unknown",
    var duration: Duration? = Duration.ofDays(1L),
    var pardoned: Boolean = false,
    var type: Type = Type.MUTE,
) : Entity<ObjectId> {
    val expired: Boolean get() = duration != null && (pardoned || timestamp.plus(duration).isBefore(Instant.now()))
    val remaining: Duration get() = duration?.minus(Duration.between(Instant.now(), timestamp)) ?: Duration.ZERO
    enum class Type {
        MUTE, KICK, BAN
    }
}
