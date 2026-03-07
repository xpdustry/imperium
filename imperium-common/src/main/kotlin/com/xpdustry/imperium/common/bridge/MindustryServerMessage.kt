// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.bridge

import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

@Serializable data class MindustryServerMessage(val server: String, val message: String, val chat: Boolean) : Message
