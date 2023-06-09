/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.common.database.model

import com.xpdustry.foundation.common.database.Entity
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import org.bson.types.ObjectId

class Punishment(
    override val identifier: ObjectId,
    var targets: List<InetAddress> = emptyList(),
    var kind: Kind = Kind.KICK,
    var reason: String = "Unknown",
    var duration: Duration = Duration.ZERO,
    var pardonned: Boolean = false,
) : Entity<ObjectId> {

    val expired: Boolean
        get() = pardonned || timestamp.plus(duration).isBefore(Instant.now())

    val active: Boolean
        get() = !expired

    val timestamp: Instant
        get() = identifier.date.toInstant()

    val expiration: Instant
        get() = timestamp.plus(duration)

    val remaining: Duration
        get() =
            if (expiration.isBefore(Instant.now())) {
                Duration.ZERO
            } else Duration.between(Instant.now(), expiration)

    enum class Kind {
        MUTE,
        KICK,
        BAN,
    }
}
