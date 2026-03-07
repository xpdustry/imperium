// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.processing

import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.misc.buildAsyncCache
import com.xpdustry.imperium.common.misc.getSuspending
import kotlin.time.Duration
import kotlin.time.toJavaDuration

fun interface Processor<I : Any, O : Any> {
    suspend fun process(context: I): O
}

class CachingProcessor<I : Any, O : Any, K : Any>(
    expiration: Duration,
    private val extractor: (I) -> K,
    private val delegate: Processor<I, O>,
) : Processor<I, O> {
    private val cache = buildAsyncCache<K, O> { expireAfterWrite(expiration.toJavaDuration()) }

    override suspend fun process(context: I): O = cache.getSuspending(extractor(context)) { delegate.process(context) }
}

fun <I : Any, O : Any, K : Any> ProcessorPipeline<I, O>.registerCaching(
    name: String,
    expiration: Duration,
    extractor: (I) -> K,
    priority: Priority = Priority.NORMAL,
    processor: Processor<I, O>,
) = register(name, priority, CachingProcessor(expiration, extractor, processor))
