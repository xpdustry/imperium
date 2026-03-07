// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.history.config

import com.xpdustry.imperium.mindustry.history.HistoryEntry
import mindustry.ctype.UnlockableContent
import mindustry.gen.Building

object BaseBlockConfigProvider : BlockConfig.Provider<Building> {
    override fun create(building: Building, type: HistoryEntry.Type, config: Any?): BlockConfig? {
        if (isContentConfigurableBlockOnly(building)) {
            if (config == null) {
                return BlockConfig.Reset
            } else if (config is UnlockableContent) {
                return BlockConfig.Content(config)
            }
        } else if (isEnablingBlockOnly(building)) {
            if (config is Boolean) {
                return BlockConfig.Enable(config)
            }
        }
        return null
    }

    private fun isContentConfigurableBlockOnly(building: Building) =
        building.block.configurations.keys().all {
            UnlockableContent::class.java.isAssignableFrom(it) || it == Void.TYPE
        }

    private fun isEnablingBlockOnly(building: Building) =
        building.block.configurations.let { it.size == 1 && it.containsKey(Boolean::class.java) }
}
