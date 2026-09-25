// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.world

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MapRotationTest {

    @Test
    fun `test games since last played`() {
        assertEquals(mapOf(1 to 1, 2 to 0, 3 to 4, 4 to 4), gamesSinceLastPlayed(listOf(1, 2, 3, 4), listOf(2, 1, 2)))
    }

    @Test
    fun `test games since last played is capped to the pool size`() {
        assertEquals(mapOf(1 to 2, 2 to 2), gamesSinceLastPlayed(listOf(1, 2), listOf(3, 3, 3, 1)))
    }

    @Test
    fun `test pick from empty or single map pool`() {
        assertNull(pickNextMap(emptyList(), listOf(1)))
        assertEquals(1, pickNextMap(listOf(1), listOf(1)))
    }

    @Test
    fun `test last played map is never picked`() {
        val random = Random(42)
        repeat(1000) { assertNotEquals(1, pickNextMap(listOf(1, 2, 3), listOf(1, 2), random)) }
    }

    @Test
    fun `test picks are weighted by games since last played`() {
        val random = Random(42)
        val runs = 10_000
        val picks = List(runs) { pickNextMap(listOf(1, 2, 3), listOf(1, 2, 3), random) }.groupingBy { it }.eachCount()
        // Weights are 0, 1 and 4, so 2 is picked 20% of the time and 3 80% of the time
        assertEquals(null, picks[1])
        assertEquals(0.2, picks.getValue(2).toDouble() / runs, 0.02)
        assertEquals(0.8, picks.getValue(3).toDouble() / runs, 0.02)
    }

    @Test
    fun `test never played maps get the highest weight`() {
        val random = Random(42)
        val runs = 10_000
        val picks = List(runs) { pickNextMap(listOf(1, 2, 3), listOf(1, 2), random) }.groupingBy { it }.eachCount()
        // Weights are 0, 1 and 9 (never played, capped to the pool size)
        assertEquals(0.9, picks.getValue(3).toDouble() / runs, 0.02)
    }
}
