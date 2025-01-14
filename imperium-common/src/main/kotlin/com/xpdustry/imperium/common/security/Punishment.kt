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
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.misc.MindustryUUIDAsLong
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlinx.serialization.Serializable

data class Punishment(
    val id: Int,
    val target: Int,
    val reason: String,
    val type: Type,
    val duration: Duration,
    val pardon: Pardon?,
    val server: String,
    val creation: Instant,
) {
    val expired: Boolean
        get() = pardon != null || (expiration ?: Instant.MAX) < Instant.now()

    val expiration: Instant?
        get() = if (duration.isInfinite()) null else creation.plus(duration.toJavaDuration())

    val remaining: Duration?
        get() {
            val expiration = expiration ?: return null
            return java.time.Duration.between(Instant.now(), expiration).toKotlinDuration()
        }

    data class Pardon(val timestamp: Instant, val reason: String)

    enum class Type {
        MUTE,
        BAN,
        FREEZE,
    }

    @Serializable
    sealed interface Metadata {

        @Serializable data object None : Metadata

        @Serializable
        data class Votekick(
            val starter: MindustryUUIDAsLong,
            val yes: Set<MindustryUUIDAsLong>,
            val nay: Set<MindustryUUIDAsLong>,
        ) : Metadata
    }
}
