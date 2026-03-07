// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.bridge

import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

@Serializable
data class BridgeChatMessage(val serverName: String, val senderName: String, val discord: Long, val message: String) :
    Message
