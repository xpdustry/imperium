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
import kotlin.math.max
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

// This is a shameless port of unified-metrics (https://github.com/Cubxity/UnifiedMetrics).
class InfluxDBRegistry(
    private val server: ServerConfig,
    private val config: MetricConfig.InfluxDB,
    private val http: OkHttpClient
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
                    .build())

        write = client.makeWriteApi()

        ImperiumScope.IO.launch {
            val interval = config.interval.inWholeMilliseconds
            while (isActive) {
                val elapsed = measureTimeMillis {
                    try {
                        write()
                    } catch (error: Throwable) {
                        logger.error("An error occurred whilst writing samples to InfluxDB", error)
                    }
                }
                logger.debug("Collected metrics, took {} milliseconds", elapsed)
                delay(max(0, interval - elapsed))
            }
        }
    }

    override fun onImperiumExit() {
        write.close()
        client.close()
    }

    private suspend fun write() {
        collectors.forEach { collector ->
            collector.collect().forEach { metric ->
                val point = Point(metric.name)
                point.addTags(metric.labels)
                point.addTag("server", server.name)
                when (metric) {
                    is GaugeMetric -> point.addField("gauge", metric.value)
                    is CounterMetric -> point.addField("counter", metric.value)
                }
                write.writePoint(point)
            }
        }
        write.flush()
    }

    override fun register(collector: MetricsCollector) {
        collectors += collector
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
