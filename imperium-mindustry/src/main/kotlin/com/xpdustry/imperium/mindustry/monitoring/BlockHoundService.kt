// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.monitoring

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.webhook.WebhookChannel
import com.xpdustry.imperium.common.webhook.WebhookMessage
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import java.io.ByteArrayInputStream
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType

@Inject
class BlockHoundService(private val config: ImperiumConfig, private val webhook: WebhookMessageSender) :
    ImperiumApplication.Listener {

    private var job: Job? = null
    private var lastWarn: Instant? = null

    override fun onImperiumInit() {
        job =
            ImperiumScope.MAIN.launch {
                while (isActive) {
                    val blocked =
                        runCatching { runMindustryThread(timeout = 10.seconds) { /* Nothin' */ } }
                            .fold(
                                onSuccess = { false },
                                onFailure = { error ->
                                    if (error is TimeoutCancellationException) true
                                    else {
                                        logger.error(
                                            "An unexpected exception occurred while running the block hound",
                                            error,
                                        )
                                        false
                                    }
                                },
                            )
                    val now = Clock.System.now()
                    if (blocked && (lastWarn ?: Instant.DISTANT_PAST) + 1.hours < now) {
                        lastWarn = now
                        val candidates =
                            Thread.getAllStackTraces().entries.filter { (thread, stack) ->
                                thread.name.contains("HeadlessApplication", ignoreCase = true) ||
                                    stack.any { it.className.startsWith("mindustry", ignoreCase = true) }
                            }
                        webhook.send(
                            WebhookChannel.CONSOLE,
                            WebhookMessage(
                                content =
                                    buildString {
                                        config.discord.alertsRole?.let { appendLine("<@&$it>") }
                                        appendLine(
                                            "**Warning:** The main thread is blocked. Wake up the sysadmins, manual intervention is required."
                                        )
                                    },
                                attachments = listOf(createThreadDumpAttachment(candidates)),
                            ),
                        )
                    }
                    delay(5.seconds)
                }
            }
    }

    override fun onImperiumExit() {
        job?.cancel()
    }

    private fun createThreadDumpAttachment(entries: List<Map.Entry<Thread, Array<StackTraceElement>>>) =
        WebhookMessage.Attachment(
            filename = "blocked-threads.txt",
            description = "Thread dump captured during a main thread stall",
            type = "text/plain; charset=utf-8".toMediaType(),
        ) {
            ByteArrayInputStream(
                buildString {
                        if (entries.isEmpty()) {
                            append("Unable to identify blocked threads.")
                        } else {
                            entries.forEach { (thread, stack) ->
                                appendLine("Thread: ${thread.name}")
                                appendLine("State: ${thread.state}")
                                appendLine("Daemon: ${thread.isDaemon}")
                                appendLine()
                                stack.forEach { appendLine("\tat $it") }
                                appendLine()
                            }
                        }
                    }
                    .toByteArray()
            )
        }

    companion object {
        private val logger by LoggerDelegate()
    }
}
