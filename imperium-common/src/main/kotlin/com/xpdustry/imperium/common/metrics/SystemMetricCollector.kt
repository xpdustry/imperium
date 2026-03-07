// SPDX-License-Identifier: GPL-3.0-only
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
