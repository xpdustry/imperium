// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.metrics

import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.MetricConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.database.SQLDatabaseImpl
import java.nio.file.Path
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SQLMetricsRegistryTest {
    @TempDir private lateinit var tempDir: Path

    private lateinit var database: SQLDatabaseImpl
    private lateinit var registry: SQLMetricsRegistry

    @BeforeEach
    fun init() {
        database =
            SQLDatabaseImpl(
                ImperiumConfig(database = DatabaseConfig.H2(database = UUID.randomUUID().toString())),
                tempDir,
            )
        database.onImperiumInit()
        registry =
            SQLMetricsRegistry(
                ServerConfig("metric-test"),
                MetricConfig.SQL(interval = 50.milliseconds, retention = 1.seconds),
                database,
            )
        registry.onImperiumInit()
    }

    @AfterEach
    fun exit() {
        registry.onImperiumExit()
        database.onImperiumExit()
    }

    @Test
    fun `test metric samples are persisted in sql`() = runBlocking {
        val counter = Counter("mindustry_events_join_total", mapOf("mode" to "survival", "scope" to "test"))
        counter += 3
        registry.register(counter)

        val rows =
            withTimeout(5.seconds) {
                while (true) {
                    val sample =
                        database.transaction {
                            """
                            SELECT `server`, `name`, `kind`, `labels`, `value`
                            FROM `metric_sample_v2`
                            WHERE `server` = ? AND `name` = ?
                            ORDER BY `counter` DESC
                            LIMIT 1;
                            """
                                .asPreparedStatement()
                                .push("metric-test")
                                .push("mindustry_events_join_total")
                                .executeSelect {
                                    MetricRow(
                                        getString("server")!!,
                                        getString("name")!!,
                                        getString("kind")!!,
                                        getString("labels")!!,
                                        getDouble("value")!!,
                                    )
                                }
                        }
                    if (sample.isNotEmpty()) {
                        return@withTimeout sample
                    }
                    delay(25.milliseconds)
                }
                error("unreachable")
            }

        assertFalse(rows.isEmpty())
        assertEquals(
            MetricRow(
                "metric-test",
                "mindustry_events_join_total",
                "counter",
                """{"mode":"survival","scope":"test"}""",
                3.0,
            ),
            rows.first(),
        )
    }

    private data class MetricRow(
        val server: String,
        val name: String,
        val kind: String,
        val labels: String,
        val value: Double,
    )
}
