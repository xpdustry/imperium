// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.metrics

import com.xpdustry.imperium.common.misc.buildCache
import java.util.concurrent.atomic.DoubleAdder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

fun interface MetricsCollector {
    suspend fun collect(): List<Metric>
}

class Counter(private val name: String, private val labels: Labels = emptyMap()) : MetricsCollector {

    private val adder = DoubleAdder()

    override suspend fun collect() = listOf(CounterMetric(name, adder.sum(), labels))

    operator fun inc(): Counter = apply { adder.add(1.0) }

    operator fun plusAssign(delta: Double) {
        adder.add(delta)
    }

    operator fun plusAssign(delta: Number) {
        adder.add(delta.toDouble())
    }
}

class UniqueCounter<K : Any>(
    private val name: String,
    private val labels: Labels = emptyMap(),
    private val period: Duration = 1.days,
) : MetricsCollector {

    private val cache = buildCache<K, Unit> { expireAfterWrite(period.toJavaDuration()) }

    override suspend fun collect() = listOf(CounterMetric(name, cache.asMap().values.size, labels))

    operator fun inc(key: K): UniqueCounter<K> = apply { cache.put(key, Unit) }

    operator fun plusAssign(key: K) {
        cache.put(key, Unit)
    }
}
