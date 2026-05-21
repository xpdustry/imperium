// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.misc

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

suspend fun <K : Any, V : Any> AsyncCache<K, V>.getSuspending(
    key: K,
    scope: CoroutineScope,
    compute: suspend (K) -> V,
): V = get(key) { k, _ -> scope.async(Dispatchers.IO) { compute(k) }.asCompletableFuture() }.await()
