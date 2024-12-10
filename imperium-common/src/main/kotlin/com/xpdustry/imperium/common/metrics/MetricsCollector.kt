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
package com.xpdustry.imperium.common.metrics

import java.util.concurrent.atomic.DoubleAdder

fun interface MetricsCollector {
    suspend fun collect(): List<Metric>
}

class Counter(private val name: String, private val labels: Labels = emptyMap()) :
    MetricsCollector {

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
