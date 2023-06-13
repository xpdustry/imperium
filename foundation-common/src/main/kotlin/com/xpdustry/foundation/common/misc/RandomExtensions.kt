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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.Locale
import kotlin.reflect.KClass

// TODO: Cleanup this file ?

fun String.capitalize(locale: Locale = Locale.ROOT, all: Boolean = false): String {
    if (all) {
        return split(" ").joinToString(" ") { it.capitalize(locale) }
    }
    return if (isBlank()) "" else this[0].uppercase(locale) + this.substring(1)
}

fun Color.toHexString(): String =
    if (this.alpha == 255) String.format("#%02x%02x%02x", red, green, blue) else String.format("#%02x%02x%02x%02x", alpha, red, green, blue)

fun logger(name: String): Logger =
    LoggerFactory.getLogger(name)

fun logger(klass: KClass<*>): Logger =
    LoggerFactory.getLogger(klass.java)
