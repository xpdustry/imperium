// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.metrics

interface MetricsRegistry {
    fun register(collector: MetricsCollector)

    data object None : MetricsRegistry {
        override fun register(collector: MetricsCollector) = Unit
    }
}
