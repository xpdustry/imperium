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
