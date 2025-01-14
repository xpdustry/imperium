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

// Custom encoder for ids between 0 and 2^30 exclusive
// Using the 31st bit to guarantee the length of 6
// And using some math to make the generated strings feel random but still unique
// - https://www.omnicalculator.com/math/inverse-modulo
// - https://planetcalc.com/3311/
object ImperiumC6B36Codec : IdentifierCodec {

    internal const val MAX_VALUE = 1 shl 30 // 31st bit
    private const val PRIME = 997_991 // According to chatgpt, bigger primer is better so...
    private const val PRIME_MULTIPLICATIVE_INVERSE = 430_522_711

    override fun encode(identifier: Int): String {
        require(identifier >= 0) { "identifier must be non-negative" }
        require(identifier < MAX_VALUE) { "identifier must be less or equal than 2^30" }
        val scrambled = ((identifier.toLong() * PRIME) % MAX_VALUE).toInt()
        return (scrambled or MAX_VALUE).toString(36)
    }

    override fun decode(identifier: String): Int {
        val number = identifier.lowercase().toInt(36)
        require(number >= 0) { "encoded identifier must be non-negative" }
        require(number <= (MAX_VALUE or (MAX_VALUE - 1))) { "encoded identifier is bigger than 2^30" }
        val scrambled = number and (MAX_VALUE - 1)
        return ((scrambled.toLong() * PRIME_MULTIPLICATIVE_INVERSE) % MAX_VALUE).toInt()
    }
}
