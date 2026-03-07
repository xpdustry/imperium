// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

enum class Rank {
    EVERYONE,
    VERIFIED,
    OVERSEER,
    MODERATOR,
    ADMIN,
    OWNER;

    fun getRanksBelow() = entries.slice(0..this.ordinal)
}

@Serializable data class RankChangeEvent(val account: Int) : Message
