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
package com.xpdustry.foundation.common.misc

import java.util.Locale

// https://stackoverflow.com/a/3782185
class Country private constructor(val code: String) : Comparable<Country> {
    private val locale = Locale.Builder()
        .setRegion(code)
        .build()

    fun getName(locale: Locale = Locale.ROOT): String =
        this.locale.getDisplayCountry(locale)

    override fun compareTo(other: Country): Int =
        getName().compareTo(other.getName())

    override fun toString(): String = code

    companion object {
        val ALL: List<Country>
        private val INDEX: Map<String, Country>

        init {
            val map = mutableMapOf<String, Country>()
            for (code in Locale.getISOCountries()) {
                map[code] = Country(code)
            }
            INDEX = map
            ALL = map.values.toList().sorted()
        }

        operator fun get(code: String): Country? {
            return INDEX[code.uppercase(Locale.ROOT)]
        }
    }
}
