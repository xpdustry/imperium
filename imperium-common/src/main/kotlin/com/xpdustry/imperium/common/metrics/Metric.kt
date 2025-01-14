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

const val NANOSECONDS_PER_MILLISECOND: Double = 1E6
const val NANOSECONDS_PER_SECOND: Double = 1E9
const val MILLISECONDS_PER_SECOND: Double = 1E3

typealias Labels = Map<String, String>

// This is a shameless port of unified-metrics (https://github.com/Cubxity/UnifiedMetrics).
sealed interface Metric {
    val name: String
    val labels: Labels
}

data class GaugeMetric(override val name: String, val value: Double, override val labels: Labels = emptyMap()) :
    Metric {
    constructor(name: String, value: Number, labels: Labels = emptyMap()) : this(name, value.toDouble(), labels)
}

data class CounterMetric(override val name: String, val value: Double, override val labels: Labels = emptyMap()) :
    Metric {
    constructor(name: String, value: Number, labels: Labels = emptyMap()) : this(name, value.toDouble(), labels)
}
