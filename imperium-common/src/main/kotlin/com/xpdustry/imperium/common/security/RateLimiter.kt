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
package com.xpdustry.imperium.common.security

import com.google.common.cache.CacheBuilder
import kotlin.time.Duration
import kotlin.time.toJavaDuration

interface RateLimiter<K : Any> {
    fun incrementAndCheck(key: K): Boolean
}

class SimpleRateLimiter<K : Any>(private val limit: Int, period: Duration) : RateLimiter<K> {

    init {
        require(limit > 0) { "Limit must be positive" }
        require(period > Duration.ZERO) { "Period must be positive" }
    }

    private val cache =
        CacheBuilder.newBuilder().expireAfterWrite(period.toJavaDuration()).build<K, Int>()

    override fun incrementAndCheck(key: K): Boolean {
        val attempts = cache.getIfPresent(key) ?: 0
        if (attempts >= limit) {
            return false
        }
        cache.put(key, attempts + 1)
        return true
    }
}
