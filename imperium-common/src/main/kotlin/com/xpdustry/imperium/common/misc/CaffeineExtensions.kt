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
package com.xpdustry.imperium.common.misc

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.xpdustry.imperium.common.async.ImperiumScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await

fun <K : Any, V : Any> buildCache(configure: Caffeine<K, V>.() -> Unit): Cache<K, V> {
    @Suppress("UNCHECKED_CAST") val builder = Caffeine.newBuilder() as Caffeine<K, V>
    configure(builder)
    return builder.build()
}

fun <K : Any, V : Any> buildAsyncCache(configure: Caffeine<K, V>.() -> Unit): AsyncCache<K, V> {
    @Suppress("UNCHECKED_CAST") val builder = Caffeine.newBuilder() as Caffeine<K, V>
    configure(builder)
    return builder.buildAsync()
}

suspend fun <K : Any, V : Any> AsyncCache<K, V>.getSuspending(key: K, compute: suspend (K) -> V): V =
    get(key) { k, _ -> ImperiumScope.IO.async { compute(k) }.asCompletableFuture() }.await()
