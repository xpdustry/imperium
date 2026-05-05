// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.telemetry

import com.xpdustry.imperium.common.misc.logger
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.KeyValue
import io.opentelemetry.api.common.Value
import io.opentelemetry.api.common.ValueType
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import java.nio.ByteBuffer

internal object FriendlyLogRecordExporter : LogRecordExporter {
    private val LOGGER = logger("open-telemetry")

    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        try {
            logs.forEach(::export)
            return CompletableResultCode.ofSuccess()
        } catch (e: Exception) {
            LOGGER.error("Failed to export OpenTelemetry logs.", e)
            return CompletableResultCode.ofExceptionalFailure(e)
        }
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

    private fun export(logRecord: LogRecordData) {
        val message = buildString {
            append("otel")

            val scopeName = logRecord.instrumentationScopeInfo.name
            if (scopeName.isNotBlank()) {
                append('[')
                append(scopeName)
                append(']')
            }

            append(' ')
            append(logRecord.bodyValue?.let(::formatValue) ?: "<empty>")

            formatAttributes(logRecord.attributes)?.let {
                append(" | attrs: ")
                append(it)
            }

            formatAttributes(logRecord.instrumentationScopeInfo.attributes)?.let {
                append(" | scope attrs: ")
                append(it)
            }

            val spanContext = logRecord.spanContext
            if (spanContext.isValid) {
                append(" | trace: ")
                append(spanContext.traceId)
                append('/')
                append(spanContext.spanId)
            }
        }

        when (logRecord.severity) {
            Severity.TRACE,
            Severity.TRACE2,
            Severity.TRACE3,
            Severity.TRACE4 -> LOGGER.trace(message)

            Severity.DEBUG,
            Severity.DEBUG2,
            Severity.DEBUG3,
            Severity.DEBUG4 -> LOGGER.debug(message)

            Severity.INFO,
            Severity.INFO2,
            Severity.INFO3,
            Severity.INFO4 -> LOGGER.info(message)

            Severity.WARN,
            Severity.WARN2,
            Severity.WARN3,
            Severity.WARN4 -> LOGGER.warn(message)

            Severity.ERROR,
            Severity.ERROR2,
            Severity.ERROR3,
            Severity.ERROR4,
            Severity.FATAL,
            Severity.FATAL2,
            Severity.FATAL3,
            Severity.FATAL4 -> LOGGER.error(message)

            else -> LOGGER.info(message)
        }
    }

    private fun formatAttributes(attributes: Attributes): String? {
        if (attributes.isEmpty) {
            return null
        }

        return attributes
            .asMap()
            .entries
            .sortedBy { it.key.key }
            .joinToString(", ") { (key, value) -> "${key.key}=${formatAttributeValue(value)}" }
    }

    private fun formatAttributeValue(value: Any?): String =
        when (value) {
            null -> "null"
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { formatAttributeValue(it) }
            is ByteArray -> "<${value.size} bytes>"
            else -> value.toString()
        }

    private fun formatValue(value: Value<*>): String =
        when (value.type) {
            ValueType.STRING,
            ValueType.BOOLEAN,
            ValueType.LONG,
            ValueType.DOUBLE -> value.value.toString()

            ValueType.ARRAY -> {
                @Suppress("UNCHECKED_CAST")
                (value.value as List<Value<*>>).joinToString(prefix = "[", postfix = "]") { formatValue(it) }
            }

            ValueType.KEY_VALUE_LIST -> {
                @Suppress("UNCHECKED_CAST")
                (value.value as List<KeyValue>).joinToString(prefix = "{", postfix = "}") {
                    "${it.key}=${formatValue(it.value)}"
                }
            }

            ValueType.BYTES -> "<${(value.value as ByteBuffer).remaining()} bytes>"
        }
}
