// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.metrics

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.MetricConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.database.SQLDatabase
import com.xpdustry.imperium.common.misc.LoggerDelegate
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.measureTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class SQLMetricsRegistry(
    private val server: ServerConfig,
    private val config: MetricConfig.SQL,
    private val database: SQLDatabase,
) : MetricsRegistry, ImperiumApplication.Listener {
    private val collectors = CopyOnWriteArraySet<MetricsCollector>()
    private val lock = Mutex()
    private lateinit var job: Job
    private var lastCleanupAt = Instant.DISTANT_PAST

    override fun onImperiumInit() {
        require(config.interval.isPositive()) { "Metric interval must be positive" }
        require(config.retention.isPositive()) { "Metric retention must be positive" }

        runBlocking { createSchema() }

        job =
            ImperiumScope.IO.launch {
                while (isActive) {
                    var count = 0
                    val elapsed = measureTime {
                        try {
                            count = write(shouldCleanup())
                        } catch (error: Throwable) {
                            logger.error("An error occurred whilst writing samples to SQL", error)
                        }
                    }
                    logger.trace("Collected {} metrics, took {} milliseconds", count, elapsed)
                    delay(config.interval)
                }
            }
    }

    override fun onImperiumExit() {
        runBlocking { job.cancelAndJoin() }
    }

    override fun register(collector: MetricsCollector) {
        collectors += collector
    }

    private suspend fun createSchema() {
        database.transaction {
            """
            CREATE TABLE IF NOT EXISTS `metric_sample_v2` (
                `counter`       BIGINT          NOT NULL AUTO_INCREMENT,
                `created_at`    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                `server`        VARCHAR(64)     NOT NULL,
                `name`          VARCHAR(128)    NOT NULL,
                `kind`          VARCHAR(16)     NOT NULL,
                `labels`        TEXT            NOT NULL,
                `value`         DOUBLE          NOT NULL,
                CONSTRAINT `pk_metric_sample_v2`
                    PRIMARY KEY (`counter`)
            );
            """
                .asPreparedStatement()
                .executeSingleUpdate()

            """
            CREATE INDEX IF NOT EXISTS `ix_metric_sample_v2__created_at`
                ON `metric_sample_v2` (`created_at`);
            """
                .asPreparedStatement()
                .executeSingleUpdate()

            """
            CREATE INDEX IF NOT EXISTS `ix_metric_sample_v2__series`
                ON `metric_sample_v2` (`server`, `name`, `kind`);
            """
                .asPreparedStatement()
                .executeSingleUpdate()
        }
    }

    private suspend fun write(cleanup: Boolean): Int =
        lock.withLock {
            val samples = mutableListOf<MetricSample>()
            collectors.forEach { collector ->
                collector.collect().forEach { metric ->
                    samples +=
                        MetricSample(
                            metric.name,
                            metric.kind,
                            LABELS_JSON.encodeToString(LABELS_SERIALIZER, metric.labels.toSortedMap()),
                            metric.value,
                        )
                }
            }

            database.transaction {
                samples.forEach { sample ->
                    """
                    INSERT INTO `metric_sample_v2` (`server`, `name`, `kind`, `labels`, `value`)
                    VALUES (?, ?, ?, ?, ?);
                    """
                        .asPreparedStatement()
                        .push(server.name)
                        .push(sample.name)
                        .push(sample.kind)
                        .push(sample.labels)
                        .push(sample.value)
                        .executeUpdate()
                }

                if (cleanup) {
                    "DELETE FROM `metric_sample_v2` WHERE `created_at` < ?;"
                        .asPreparedStatement()
                        .push(Clock.System.now() - config.retention)
                        .executeUpdate()
                    lastCleanupAt = Clock.System.now()
                }
            }

            samples.size
        }

    private fun shouldCleanup(): Boolean {
        return Clock.System.now() > lastCleanupAt + CLEANUP_INTERVAL.coerceAtMost(config.retention)
    }

    private data class MetricSample(val name: String, val kind: String, val labels: String, val value: Double)

    private val Metric.kind: String
        get() =
            when (this) {
                is CounterMetric -> "counter"
                is GaugeMetric -> "gauge"
            }

    private val Metric.value: Double
        get() =
            when (this) {
                is CounterMetric -> value
                is GaugeMetric -> value
            }

    companion object {
        private val logger by LoggerDelegate()
        private val LABELS_JSON = Json { explicitNulls = false }
        private val LABELS_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
        private val CLEANUP_INTERVAL = 10.minutes
    }
}
