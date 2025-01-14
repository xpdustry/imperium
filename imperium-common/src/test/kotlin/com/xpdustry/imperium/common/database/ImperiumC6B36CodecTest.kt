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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail

class ImperiumC6B36CodecTest {

    @Test
    fun `test uniqueness`() {
        val array = BooleanArray(ImperiumC6B36Codec.MAX_VALUE - 1)
        for (i in 0 until ImperiumC6B36Codec.MAX_VALUE - 1) {
            val encoded = ImperiumC6B36Codec.encode(i)
            val decoded = ImperiumC6B36Codec.decode(encoded)
            if (array[decoded]) fail("Duplicate: $i -> $encoded -> $decoded")
            array[decoded] = true
        }
    }

    @Test
    fun `test encode validation`() {
        assertThrows<IllegalArgumentException> { ImperiumC6B36Codec.encode(-1) }
        assertThrows<IllegalArgumentException> { ImperiumC6B36Codec.encode(ImperiumC6B36Codec.MAX_VALUE) }
    }

    @Test
    fun `test decode validation`() {
        assertThrows<IllegalArgumentException> { ImperiumC6B36Codec.decode("-1") }
        assertThrows<IllegalArgumentException> {
            ImperiumC6B36Codec.decode((ImperiumC6B36Codec.MAX_VALUE shl 1).toLong().toString(36))
        }
        assertThrows<IllegalArgumentException> { ImperiumC6B36Codec.decode("zzzzzz") }
    }
}
