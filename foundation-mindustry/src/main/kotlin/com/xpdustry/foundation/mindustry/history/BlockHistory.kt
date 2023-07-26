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
package com.xpdustry.foundation.mindustry.history

import com.xpdustry.foundation.common.database.MindustryUUID
import mindustry.gen.Building

interface BlockHistory {
    fun <B : Building> setConfigurationFactory(clazz: Class<B>, factory: HistoryConfig.Factory<B>)
    fun getHistory(x: Int, y: Int): List<HistoryEntry>
    fun getHistory(uuid: MindustryUUID): List<HistoryEntry>
}
