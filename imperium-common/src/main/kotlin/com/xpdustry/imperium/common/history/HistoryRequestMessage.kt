// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.history

import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

@Serializable data class HistoryRequestMessage(val server: String, val player: Int, val id: String) : Message

@Serializable data class HistoryResponseMessage(val history: String, val id: String) : Message
