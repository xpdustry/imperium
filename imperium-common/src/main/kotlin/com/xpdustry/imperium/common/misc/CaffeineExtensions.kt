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
package com.xpdustry.imperium.common.misc

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.xpdustry.imperium.common.async.ImperiumScope
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await

// TODO Replace by builder block
fun <K, V> buildAsyncCache(
    expireAfterAccess: Duration? = null,
    expireAfterWrite: Duration? = null,
    maximumSize: Long = -1
): AsyncCache<K, V> {
    val builder = Caffeine.newBuilder()
    if (expireAfterAccess != null) {
        builder.expireAfterAccess(expireAfterAccess.toJavaDuration())
    }
    if (expireAfterWrite != null) {
        builder.expireAfterWrite(expireAfterWrite.toJavaDuration())
    }
    if (maximumSize > 0) {
        builder.maximumSize(maximumSize)
    }
    return builder.buildAsync()
}

fun <K, V> buildCache(configure: Caffeine<K, V>.() -> Unit): Cache<K, V> {
    @Suppress("UNCHECKED_CAST") val builder = Caffeine.newBuilder() as Caffeine<K, V>
    configure(builder)
    return builder.build()
}

suspend fun <K, V> AsyncCache<K, V>.getSuspending(key: K, compute: suspend (K) -> V): V =
    get(key) { k, _ -> ImperiumScope.IO.async { compute(k) }.asCompletableFuture() }.await()
