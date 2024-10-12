/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
        building.block().configurations.keys().all {
            UnlockableContent::class.java.isAssignableFrom(it) || it == Void.TYPE
        }

    private fun isEnablingBlockOnly(building: Building) =
        building.block().configurations.let { it.size == 1 && it.containsKey(Boolean::class.java) }
}
