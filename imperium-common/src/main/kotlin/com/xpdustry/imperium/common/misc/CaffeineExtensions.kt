// SPDX-License-Identifier: GPL-3.0-only
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
