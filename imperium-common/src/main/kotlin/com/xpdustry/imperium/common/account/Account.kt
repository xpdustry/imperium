// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import kotlin.time.Duration
import kotlin.time.Instant

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
