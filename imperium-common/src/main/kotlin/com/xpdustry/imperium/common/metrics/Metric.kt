// SPDX-License-Identifier: GPL-3.0-only
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
