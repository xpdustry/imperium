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
package com.xpdustry.imperium.common.misc

import com.google.common.primitives.Longs
import java.awt.Color
import java.util.Base64
import java.util.zip.CRC32

typealias MindustryUUID = String

typealias MindustryUSID = String

val MINDUSTRY_ACCENT_COLOR = Color(0xffd37f)

val MINDUSTRY_ORANGE_COLOR = Color(0xffa108)

fun CharSequence.stripMindustryColors(): String {
    val out = StringBuilder(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char == '[') {
            if (getOrNull(index + 1) == '[') {
                out.append(char)
                index += 2
            } else {
                while (index < length && this[index] != ']') {
                    index++
                }
                index++
            }
        } else {
            out.append(char)
            index++
        }
    }
    return out.toString()
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
        Longs.fromBytes(
            bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15])
}

fun MindustryUUID.toShortMuuid(): ByteArray = Base64.getDecoder().decode(this).sliceArray(0..7)

fun MindustryUUID.toLong(): Long = Longs.fromByteArray(toShortMuuid())

fun Long.toCRC32Muuid(): MindustryUUID = Longs.toByteArray(this).toCRC32Muuid()
