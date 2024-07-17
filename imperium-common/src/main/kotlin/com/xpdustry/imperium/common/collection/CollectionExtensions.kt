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
package com.xpdustry.imperium.common.collection

import java.util.EnumSet

fun <T : Any> List<T>.findMostCommon(): T? {
    val map = mutableMapOf<T, Int>()
    var max: T? = null
    for (key in this) {
        val value = (map[key] ?: 0) + 1
        map[key] = value
        if (max == null || value > map[max]!!) {
            max = key
            map[max] = value
        }
    }
    return max
}

inline fun <reified T : Enum<T>> enumSetOf(vararg elements: T): Set<T> =
    EnumSet.noneOf(T::class.java).apply { addAll(elements) }

inline fun <reified T : Enum<T>> enumSetAllOf(): Set<T> = EnumSet.allOf(T::class.java)
