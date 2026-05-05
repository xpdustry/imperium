// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.telemetry

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.application.ImperiumPlatform
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.version.ImperiumVersion
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import kotlin.time.toJavaDuration

@Inject
class OpenTelemetryService(
    private val config: ImperiumConfig,
    private val version: ImperiumVersion,
    private val platform: ImperiumPlatform,
) : ImperiumApplication.Listener {

    private lateinit var sdk: OpenTelemetrySdk

    override fun onImperiumInit() {
        val resource =
            Resource.getDefault()
                .merge(
                    Resource.create(
                        Attributes.builder()
                            .put("service.name", config.server.name)
                            .put("service.namespace", "imperium")
                            .put("imperium.server.name", config.server.name)
                            .put("imperium.server.version", version.toString())
                            .put("imperium.server.platform", platform.name.lowercase())
                            .build()
                    )
                )

        val metrics = SdkMeterProvider.builder().setResource(resource)
        val traces = SdkTracerProvider.builder().setResource(resource)
        val logs =
            SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(FriendlyLogRecordExporter))

        if (config.telemetry != null) {
            val (endpoint, token, interval) = config.telemetry

            metrics.registerMetricReader(
                PeriodicMetricReader.builder(
                        OtlpHttpMetricExporter.builder()
                            .setEndpoint(endpoint.toString())
                            .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                            .build()
                    )
                    .setInterval(interval.toJavaDuration())
                    .build()
            )

            traces.addSpanProcessor(
                BatchSpanProcessor.builder(
                        OtlpHttpSpanExporter.builder()
                            .setEndpoint(endpoint.toString())
                            .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                            .build()
                    )
                    .build()
            )

            logs.addLogRecordProcessor(
                BatchLogRecordProcessor.builder(
                        OtlpHttpLogRecordExporter.builder()
                            .setEndpoint(endpoint.toString())
                            .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                            .build()
                    )
                    .build()
            )
        }

        sdk =
            OpenTelemetrySdk.builder()
                .setMeterProvider(metrics.build())
                .setLoggerProvider(logs.build())
                .setTracerProvider(traces.build())
                .build()
    }

    override fun onImperiumExit() {
        sdk.close()
    }
}
