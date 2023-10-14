/*
 * Imperium, the software collection powering the Xpdustry network.
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
package com.xpdustry.imperium.mindustry.history

import com.xpdustry.imperium.common.account.MindustryUUID

interface BlockHistory {
    fun getHistory(x: Int, y: Int): List<HistoryEntry>

    fun getHistory(uuid: MindustryUUID): List<HistoryEntry>

    fun getLatestPlace(x: Int, y: Int): HistoryEntry? =
        getHistory(x, y)
            .toMutableList()
            .dropLastWhile {
                it.type == HistoryEntry.Type.ROTATE || it.type == HistoryEntry.Type.CONFIGURE
            }
            .lastOrNull()
            ?.takeIf { it.type == HistoryEntry.Type.PLACE }
}
