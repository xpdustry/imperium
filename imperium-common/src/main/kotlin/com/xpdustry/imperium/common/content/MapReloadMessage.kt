// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.content

import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

@Serializable data class MapReloadMessage(val gamemodes: Set<MindustryGamemode>) : Message
