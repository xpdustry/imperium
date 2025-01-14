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

import com.sun.management.OperatingSystemMXBean as SunOperatingSystemMXBean
import java.lang.management.ManagementFactory

class SystemMetricCollector : MetricsCollector {
    private val os = ManagementFactory.getOperatingSystemMXBean() as? SunOperatingSystemMXBean
    private val threads = ManagementFactory.getThreadMXBean()
    private val runtime = ManagementFactory.getRuntimeMXBean()
    private val memory = ManagementFactory.getMemoryMXBean()

    override suspend fun collect(): List<Metric> {
        val result = ArrayList<Metric>()

        // Threads
        result += GaugeMetric("jvm_threads_current_count", threads.threadCount)
        result += GaugeMetric("jvm_threads_daemon_count", threads.daemonThreadCount)
        result += CounterMetric("jvm_threads_started_total", threads.totalStartedThreadCount)
        result += GaugeMetric("jvm_threads_peak", threads.peakThreadCount)

        // OS
        if (os != null) {
            result += GaugeMetric("process_cpu_load_ratio", os.processCpuLoad)
            result += CounterMetric("process_cpu_seconds_total", os.processCpuTime / NANOSECONDS_PER_SECOND)
        }

        // Runtime
        result += GaugeMetric("process_start_time_seconds", runtime.startTime / MILLISECONDS_PER_SECOND)

        // Memory
        sequenceOf(memory.heapMemoryUsage to "heap", memory.nonHeapMemoryUsage to "nonheap").forEach { (usage, area) ->
            val tags = mapOf("area" to area)
            result += GaugeMetric("jvm_memory_bytes_used", usage.used, tags)
            result += GaugeMetric("jvm_memory_bytes_committed", usage.committed, tags)
            result += GaugeMetric("jvm_memory_bytes_max", usage.max, tags)
            result += GaugeMetric("jvm_memory_bytes_init", usage.init, tags)
        }

        return result
    }
}
