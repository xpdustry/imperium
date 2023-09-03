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
package com.xpdustry.imperium.common.account

enum class Achievement(val goal: Int = 1, val secret: Boolean = false) {
    ACTIVE(7, true),
    HYPER(30, true),
    ADDICT(90, true),
    GAMER(8 * 60),
    STEAM,
    DISCORD,
    DAY(24 * 60),
    WEEK(7 * 24 * 60),
    MONTH(30 * 24 * 60),
    ;

    data class Progression(var progress: Int = 0, var completed: Boolean = false)
}
