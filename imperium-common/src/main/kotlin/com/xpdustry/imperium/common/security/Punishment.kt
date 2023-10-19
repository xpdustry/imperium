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
import com.xpdustry.imperium.common.database.snowflake.Snowflake
import com.xpdustry.imperium.common.database.snowflake.timestamp
import com.xpdustry.imperium.common.serialization.SerializableInetAddress
import com.xpdustry.imperium.common.serialization.SerializableJDuration
import com.xpdustry.imperium.common.serialization.SerializableJInstant
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Punishment(
    override val _id: Snowflake,
    var target: Target,
    var reason: String,
    val type: Type,
    var duration: SerializableJDuration?,
    var pardon: Pardon? = null,
) : Entity<Snowflake> {
    val pardoned: Boolean
        get() = pardon != null

    val expired: Boolean
        get() = pardoned || expiration < Instant.now()

    val expiration: Instant
        get() = duration?.let { _id.timestamp.plus(it) } ?: Instant.MAX

    val permanent: Boolean
        get() = duration == null

    val timestamp: Instant
        get() = _id.timestamp

    @Serializable
    data class Target(val address: SerializableInetAddress, val uuid: MindustryUUID? = null)

    @Serializable data class Pardon(val timestamp: SerializableJInstant, val reason: String)

    enum class Type {
        MUTE,
        BAN
    }
}
