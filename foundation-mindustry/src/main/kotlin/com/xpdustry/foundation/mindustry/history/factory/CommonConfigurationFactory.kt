/*
 * Foundation, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.foundation.mindustry.history.factory

import com.xpdustry.foundation.mindustry.history.HistoryConfig
import com.xpdustry.foundation.mindustry.history.HistoryEntry
import fr.xpdustry.distributor.api.util.ArcCollections
import mindustry.ctype.UnlockableContent
import mindustry.gen.Building

object CommonConfigurationFactory : HistoryConfig.Factory<Building> {
    override fun create(building: Building, type: HistoryEntry.Type, config: Any?): HistoryConfig? {
        if (isContentConfigurableBlockOnly(building)) {
            if (config == null) {
                return HistoryConfig.Content(null)
            } else if (config is UnlockableContent) {
                return HistoryConfig.Content(config)
            }
        } else if (isEnablingBlockOnly(building)) {
            if (config is Boolean) {
                return HistoryConfig.Enable(config)
            }
        }
        return null
    }

    private fun isContentConfigurableBlockOnly(building: Building): Boolean {
        for (configuration in building.block().configurations.keys()) {
            if (!(UnlockableContent::class.java.isAssignableFrom(configuration) || configuration == Void.TYPE)) {
                return false
            }
        }
        return true
    }

    private fun isEnablingBlockOnly(building: Building): Boolean {
        val keys = ArcCollections.immutableMap(building.block().configurations).keys
        return keys.size == 1 && keys.contains(Boolean::class.java)
    }
}
