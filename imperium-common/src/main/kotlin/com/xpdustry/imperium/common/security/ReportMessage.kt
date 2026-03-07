// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

@Serializable
data class ReportMessage(
    val serverName: String,
    val senderName: String,
    val senderId: Int,
    val targetName: String,
    val targetId: Int,
    val reason: Reason,
) : Message {
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
