// SPDX-License-Identifier: GPL-3.0-only
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
