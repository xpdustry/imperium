// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.metrics

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.metrics.MetricsRegistry
import com.xpdustry.imperium.common.metrics.SystemMetricCollector

@Inject
class MetricsListener constructor(private val metrics: MetricsRegistry) : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        metrics.register(SystemMetricCollector())
    }
}
