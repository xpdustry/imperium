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

import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.timestamp
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

data class Punishment(
    val snowflake: Snowflake,
    val author: Author,
    val target: Target,
    val reason: String,
    val type: Type,
    val duration: Duration,
    val pardon: Pardon?,
) {
    val expired: Boolean
        get() = pardon != null || expiration < Instant.now()

    val expiration: Instant
        get() =
            if (duration.isInfinite()) Instant.MAX
            else snowflake.timestamp.plus(duration.toJavaDuration()) ?: Instant.MAX

    val permanent: Boolean
        get() = duration.isInfinite()

    data class Target(
        val address: InetAddress,
        val uuid: MindustryUUID? = null,
        val mask: Byte = 0
    ) {
        init {
            val size = if (address is Inet4Address) 32 else 128
            require(mask in 0..size) { "mask must be in range 0..$size" }
        }
    }

    data class Author(val identifier: Long, val type: Type) {
        enum class Type {
            DISCORD,
            ACCOUNT,
            USER,
            CONSOLE
        }
    }

    data class Pardon(val timestamp: Instant, val reason: String, val author: Author)

    enum class Type {
        MUTE,
        BAN
    }
}
