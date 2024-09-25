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
package com.xpdustry.imperium.common.database

interface IdentifierCodec {
    fun encode(identifier: Int): String

    fun decode(identifier: String): Int
}

fun IdentifierCodec.tryDecode(identifier: String): Int? {
    return try {
        decode(identifier)
    } catch (e: IllegalArgumentException) {
        null
    }
}

// Custom encoding for ids between 0 and 2^30
// Encoded ids feel random, but are guaranteed to be unique
object ImperiumBase36Char6Codec : IdentifierCodec {

    private const val MAX_VALUE = 1 shl 30

    override fun encode(identifier: Int): String {
        require(identifier >= 0) { "i must be non-negative" }
        require(identifier <= MAX_VALUE) { "i must be less than 2^30" }
        return (identifier or MAX_VALUE).toString(36)
    }

    override fun decode(identifier: String): Int {
        val number = identifier.lowercase().toInt(36)
        require((number and MAX_VALUE) != 0) { "Invalid encoded number" }
        require(number >= 0) { "Invalid encoded number" }
        return number and MAX_VALUE.inv()
    }
}
