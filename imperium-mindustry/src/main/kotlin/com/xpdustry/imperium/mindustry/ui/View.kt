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
package com.xpdustry.imperium.mindustry.ui

import com.xpdustry.imperium.mindustry.ui.state.State
import mindustry.gen.Player

interface View {
    val owner: Interface
    val viewer: Player
    val parent: View?
    val state: State
    val isOpen: Boolean

    fun open()

    fun close()

    fun back(depth: Int = 1) {
        var current: View? = this
        var i = depth
        while (current != null && i-- > 0) {
            current.close()
            current = current.parent
        }
        current?.open()
    }

    fun closeAll() {
        var current: View? = this
        while (current != null) {
            current.close()
            current = current.parent
        }
    }
}
