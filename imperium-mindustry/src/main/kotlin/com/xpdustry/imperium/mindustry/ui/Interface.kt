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
import java.util.function.Consumer
import mindustry.gen.Player

// Inspired from https://github.com/Incendo/interfaces, best interface library ever :)
interface Interface {
    fun create(viewer: Player): View

    fun create(parent: View): View

    fun open(viewer: Player): View {
        val view = create(viewer)
        view.open()
        return view
    }

    fun open(viewer: Player, consumer: Consumer<State>): View {
        val view = create(viewer)
        consumer.accept(view.state)
        view.open()
        return view
    }

    fun open(parent: View): View {
        val view = create(parent)
        view.open()
        return view
    }

    fun open(parent: View, consumer: Consumer<State>): View {
        val view = create(parent)
        consumer.accept(view.state)
        view.open()
        return view
    }
}
