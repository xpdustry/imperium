// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import com.google.common.cache.CacheBuilder
import kotlin.time.Duration
import kotlin.time.toJavaDuration

interface RateLimiter<K : Any> {
    fun check(key: K): Boolean

    fun increment(key: K)

    fun incrementAndCheck(key: K): Boolean
}

class SimpleRateLimiter<K : Any>(private val limit: Int, period: Duration) : RateLimiter<K> {

    init {
        require(limit > 0) { "Limit must be positive" }
        require(period > Duration.ZERO) { "Period must be positive" }
    }

    private val cache = CacheBuilder.newBuilder().expireAfterWrite(period.toJavaDuration()).build<K, Int>()

    override fun check(key: K) = (cache.getIfPresent(key) ?: 0) < limit

    override fun increment(key: K) {
        val attempts = cache.getIfPresent(key) ?: 0
        cache.put(key, attempts + 1)
    }

    override fun incrementAndCheck(key: K): Boolean {
        val attempts = cache.getIfPresent(key) ?: 0
        if (attempts >= limit) {
            return false
        }
        cache.put(key, attempts + 1)
        return true
    }
}
