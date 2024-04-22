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
package com.xpdustry.imperium.mindustry.ui.input

import com.xpdustry.imperium.mindustry.ui.Pane
import com.xpdustry.imperium.mindustry.ui.action.Action
import com.xpdustry.imperium.mindustry.ui.action.BiAction

data class TextInputPane(
    var title: String = "",
    var description: String = "",
    var placeholder: String = "",
    var length: Int = 64,
    var inputAction: BiAction<String> = Action.none().asBiAction(),
    var exitAction: Action = Action { it.back() },
) : Pane
