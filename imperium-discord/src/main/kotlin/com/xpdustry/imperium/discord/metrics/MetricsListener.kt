// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.metrics

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.metrics.MetricsRegistry
import com.xpdustry.imperium.common.metrics.SystemMetricCollector

class MetricsListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val metrics = instances.get<MetricsRegistry>()

    override fun onImperiumInit() {
        metrics.register(SystemMetricCollector())
    }
}
