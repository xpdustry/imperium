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
package com.xpdustry.imperium.common.security

import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SimpleRateLimiterTest {
    @Test
    fun `test simple attempts`() {
        val rateLimiter = SimpleRateLimiter<Unit>(3, 1.seconds)
        Assertions.assertTrue(rateLimiter.incrementAndCheck(Unit))
        Assertions.assertTrue(rateLimiter.incrementAndCheck(Unit))
        Assertions.assertTrue(rateLimiter.incrementAndCheck(Unit))
        Assertions.assertFalse(rateLimiter.incrementAndCheck(Unit))
        Thread.sleep(1000)
        Assertions.assertTrue(rateLimiter.incrementAndCheck(Unit))
    }

    @Test
    fun `test stable attempts`() {
        val rateLimiter = SimpleRateLimiter<Unit>(1, 1.seconds)
        Assertions.assertTrue(rateLimiter.incrementAndCheck(Unit))
        Thread.sleep(1000)
        Assertions.assertTrue(rateLimiter.incrementAndCheck(Unit))
        Thread.sleep(1000)
        Assertions.assertTrue(rateLimiter.incrementAndCheck(Unit))
    }

    @Test
    fun `test different keys`() {
        val rateLimiter = SimpleRateLimiter<String>(1, 1.seconds)
        Assertions.assertTrue(rateLimiter.incrementAndCheck("a"))
        Assertions.assertFalse(rateLimiter.incrementAndCheck("a"))
        Assertions.assertTrue(rateLimiter.incrementAndCheck("b"))
        Assertions.assertFalse(rateLimiter.incrementAndCheck("b"))
        Thread.sleep(1000)
        Assertions.assertTrue(rateLimiter.incrementAndCheck("a"))
        Assertions.assertFalse(rateLimiter.incrementAndCheck("a"))
        Assertions.assertTrue(rateLimiter.incrementAndCheck("b"))
        Assertions.assertFalse(rateLimiter.incrementAndCheck("b"))
    }
}
