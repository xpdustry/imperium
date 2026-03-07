// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.network

import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

@Serializable
data class DiscoveryMessage(val info: Discovery.Server, val type: Type) : Message {
    enum class Type {
        DISCOVER,
        UN_DISCOVER,
    }
}
