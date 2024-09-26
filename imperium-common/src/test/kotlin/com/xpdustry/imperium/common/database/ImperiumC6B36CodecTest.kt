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
        assertThrows<IllegalArgumentException> { ImperiumC6B36Codec.decode((ImperiumC6B36Codec.MAX_VALUE shl 1).toLong().toString(36)) }
        assertThrows<IllegalArgumentException> { ImperiumC6B36Codec.decode("zzzzzz") }
    }
}