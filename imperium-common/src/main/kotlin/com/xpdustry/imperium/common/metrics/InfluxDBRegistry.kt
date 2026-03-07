// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.metrics

import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.WriteApi
import com.influxdb.client.write.Point
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.MetricConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.misc.LoggerDelegate
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

// This is a shameless port of unified-metrics (https://github.com/Cubxity/UnifiedMetrics).
class InfluxDBRegistry(
    private val server: ServerConfig,
    private val config: MetricConfig.InfluxDB,
    private val http: OkHttpClient,
) : MetricsRegistry, ImperiumApplication.Listener {
    private val collectors = CopyOnWriteArraySet<MetricsCollector>()
    private lateinit var client: InfluxDBClient
    private lateinit var write: WriteApi

    override fun onImperiumInit() {
        client =
            InfluxDBClientFactory.create(
                InfluxDBClientOptions.builder()
                    .url(config.endpoint.toString())
                    .authenticateToken(config.token.value.toCharArray())
                    .bucket(config.bucket)
                    .org(config.organization)
                    .okHttpClient(http.newBuilder())
                    .build()
            )

        write = client.makeWriteApi()

        ImperiumScope.IO.launch {
            val interval = config.interval.inWholeMilliseconds
            while (isActive) {
                var count = 0
                val elapsed = measureTimeMillis {
                    try {
                        count = write()
                    } catch (error: Throwable) {
                        logger.error("An error occurred whilst writing samples to InfluxDB", error)
                    }
                }
                logger.trace("Collected {} metrics, took {} milliseconds", count, elapsed)
                delay((interval - elapsed).coerceAtLeast(1))
            }
        }
    }

    override fun onImperiumExit() {
        write.close()
        client.close()
    }

    private suspend fun write(): Int {
        val points = ArrayList<Point>()
        collectors.forEach { collector ->
            collector.collect().forEach { metric ->
                val point = Point(metric.name)
                point.addTags(metric.labels)
                point.addTag("server", server.name)
                when (metric) {
                    is GaugeMetric -> point.addField("gauge", metric.value)
                    is CounterMetric -> point.addField("counter", metric.value)
                }
                points += point
            }
        }
        write.writePoints(points)
        write.flush()
        return points.size
    }

    override fun register(collector: MetricsCollector) {
        collectors += collector
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
