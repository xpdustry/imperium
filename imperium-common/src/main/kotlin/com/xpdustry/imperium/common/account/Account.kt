// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import java.time.Instant
import kotlin.time.Duration

data class Account(
    val id: Int,
    val username: String,
    val discord: Long?,
    val games: Int,
    val playtime: Duration,
    val creation: Instant,
    val legacy: Boolean,
    val rank: Rank,
)
