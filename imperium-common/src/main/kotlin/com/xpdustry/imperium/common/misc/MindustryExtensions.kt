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
package com.xpdustry.imperium.common.misc

import com.google.common.primitives.Longs
import java.awt.Color
import java.util.Base64
import java.util.zip.CRC32

typealias MindustryUUID = String

typealias MindustryUUIDAsLong = Long

typealias MindustryUSID = String

val MINDUSTRY_ACCENT_COLOR = Color(0xffd37f)

val MINDUSTRY_ORANGE_COLOR = Color(0xffa108)

private val NAMED_COLORS =
    setOf(
        "CLEAR",
        "BLACK",
        "WHITE",
        "LIGHTGRAY",
        "GRAY",
        "DARKGRAY",
        "LIGHTGREY",
        "GREY",
        "DARKGREY",
        "BLUE",
        "NAVY",
        "ROYAL",
        "SLATE",
        "SKY",
        "CYAN",
        "TEAL",
        "GREEN",
        "ACID",
        "LIME",
        "FOREST",
        "OLIVE",
        "YELLOW",
        "GOLD",
        "GOLDENROD",
        "ORANGE",
        "BROWN",
        "TAN",
        "BRICK",
        "RED",
        "SCARLET",
        "CRIMSON",
        "CORAL",
        "SALMON",
        "PINK",
        "MAGENTA",
        "PURPLE",
        "VIOLET",
        "MAROON",
        "ACCENT",
        "UNLAUNCHED",
        "HIGHLIGHT",
        "STAT",
        "NEGSTAT",
    )

// https://github.com/Anuken/Arc/blob/eddce8f1e6b9d960a38fa4dfed3c07e3e211fca2/arc-core/src/arc/util/Strings.java#L118
fun CharSequence.stripMindustryColors(): String {
    val out = StringBuilder(length)
    var i = 0
    while (i < length) {
        val c: Char = get(i)
        if (c == '[') {
            val length = parseColorMarkup(this, i + 1, length)
            if (length >= 0) {
                i += length + 2
            } else {
                out.append(c)
                i++
            }
        } else {
            out.append(c)
            i++
        }
    }

    return out.toString()
}

// I have no idea how it works
// https://github.com/Anuken/Arc/blob/eddce8f1e6b9d960a38fa4dfed3c07e3e211fca2/arc-core/src/arc/util/Strings.java#L156
private fun parseColorMarkup(str: CharSequence, start: Int, end: Int): Int {
    if (start >= end) return -1
    when (str[start]) {
        '#' -> {
            var i = start + 1
            while (i < end) {
                val ch = str[i]
                if (ch == ']') {
                    if (i < start + 2 || i > start + 9) break
                    return i - start
                }
                if (!(ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F')) {
                    break
                }
                i++
            }
            return -1
        }
        '[' -> return -2
        ']' -> return 0
    }
    for (i in start + 1 until end) {
        val ch = str[i]
        if (ch != ']') continue
        val name = str.substring(start, i).uppercase()
        return if (name in NAMED_COLORS) {
            i - start
        } else {
            -1
        }
    }
    return -1
}

fun ByteArray.toCRC32Muuid(): MindustryUUID {
    val bytes = ByteArray(16)
    copyInto(bytes, 0, 0, 8)
    val crc = CRC32()
    crc.update(this, 0, 8)
    Longs.toByteArray(crc.value).copyInto(bytes, 8)
    return Base64.getEncoder().encodeToString(bytes)
}

fun String.isCRC32Muuid(): Boolean {
    val bytes =
        try {
            Base64.getDecoder().decode(this)
        } catch (_: Exception) {
            return false
        }
    if (bytes.size != 16) {
        return false
    }
    val crc = CRC32()
    crc.update(bytes, 0, 8)
    return crc.value ==
        Longs.fromBytes(bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15])
}

fun MindustryUUID.toShortMuuid(): ByteArray = Base64.getDecoder().decode(this).sliceArray(0..7)

fun MindustryUUID.toLongMuuid(): MindustryUUIDAsLong = Longs.fromByteArray(toShortMuuid())

fun Long.toCRC32Muuid(): MindustryUUID = Longs.toByteArray(this).toCRC32Muuid()
