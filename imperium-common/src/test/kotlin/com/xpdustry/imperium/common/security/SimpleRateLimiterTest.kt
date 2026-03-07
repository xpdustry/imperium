// SPDX-License-Identifier: GPL-3.0-only
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
