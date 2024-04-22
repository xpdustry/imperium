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
package com.xpdustry.imperium.mindustry.ui.menu

import com.xpdustry.imperium.mindustry.ui.Pane
import com.xpdustry.imperium.mindustry.ui.action.Action

data class MenuPane(
    var title: String = "",
    var content: String = "",
    val options: MenuOptionGrid = MenuOptionGrid(),
    var exitAction: Action = Action { it.closeAll() },
) : Pane

class MenuOptionGrid {
    private val _grid: MutableList<MutableList<MenuOption>> = mutableListOf()

    val grid: List<List<MenuOption>>
        get() = _grid

    fun getRow(index: Int): List<MenuOption> = _grid[index]

    fun setRow(index: Int, options: List<MenuOption>) {
        _grid[index] = options.toMutableList()
    }

    fun addRow(vararg options: MenuOption) {
        _grid.add(options.toMutableList())
    }

    fun addRow(options: List<MenuOption>) {
        _grid.add(options.toMutableList())
    }

    fun addRow(index: Int, vararg options: MenuOption) {
        _grid.add(index, options.toMutableList())
    }

    fun addRow(index: Int, options: List<MenuOption>) {
        _grid.add(index, options.toMutableList())
    }

    fun removeRow(index: Int) {
        _grid.removeAt(index)
    }

    fun getOption(id: Int): MenuOption? {
        var i = 0
        for (row in _grid) {
            i += row.size
            if (i > id) {
                return row[id - i + row.size]
            }
        }
        return null
    }
}
