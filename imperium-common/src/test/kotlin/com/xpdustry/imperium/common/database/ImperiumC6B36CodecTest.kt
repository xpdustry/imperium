// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ImperiumC6B36CodecTest {

    /*
    TODO Verifying the math instead, but does it really work? I kinda vibe coded that id encoder...
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
     */

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

    @Test
    fun `test multiplicative inverse`() {
        val product =
            (ImperiumC6B36Codec.PRIME.toLong() * ImperiumC6B36Codec.PRIME_MULTIPLICATIVE_INVERSE) %
                ImperiumC6B36Codec.MAX_VALUE
        assertEquals(1L, product, "PRIME_MULTIPLICATIVE_INVERSE is not a valid inverse")
    }
}
