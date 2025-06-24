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
package com.xpdustry.imperium.common.webhook

import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.WebhookBackendConfig
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.await
import com.xpdustry.imperium.common.version.ImperiumVersion
import java.io.InputStream
import java.util.EnumMap
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface WebhookMessageSender {

    suspend fun send(channel: WebhookChannel, message: WebhookMessage)
}

class WebhookMessageSenderImpl(config: ImperiumConfig, http: OkHttpClient, version: ImperiumVersion) :
    WebhookMessageSender {
    private val channels = EnumMap<WebhookChannel, WebhookBackend>(WebhookChannel::class.java)

    init {
        config.webhooks.entries.forEach { (channel, backend) ->
            channels[channel] =
                when (backend) {
                    is WebhookBackendConfig.Discord -> DiscordWebhookBackend(http, config, backend, version)
                }
        }
    }

    override suspend fun send(channel: WebhookChannel, message: WebhookMessage) {
        channels[channel]?.send(message)
    }
}

private interface WebhookBackend {
    suspend fun send(message: WebhookMessage)
}

private class DiscordWebhookBackend(
    private val http: OkHttpClient,
    private val config: ImperiumConfig,
    private val webhookConfig: WebhookBackendConfig.Discord,
    private val version: ImperiumVersion,
) : WebhookBackend {
    override suspend fun send(message: WebhookMessage): Unit =
        withContext(ImperiumScope.IO.coroutineContext) {
            try {
                send0(message)
            } catch (e: Throwable) {
                logger.error("Fatal error sending webhook message", e)
            }
        }

    private suspend fun send0(message: WebhookMessage) {
        val payload = buildJsonObject {
            put("username", config.server.displayName)
            put("content", message.content)
            putJsonArray("embeds") {
                for (embed in message.embeds) {
                    addJsonObject {
                        put("title", embed.title)
                        embed.thumbnail?.let { media -> putJsonObject("thumbnail") { put("url", media.url) } }
                        put("description", embed.description)
                        put("color", embed.color?.rgb)
                        embed.image?.let { media -> putJsonObject("image") { put("url", media.url) } }
                    }
                }
            }
            putJsonArray("attachments") {
                for ((index, attachment) in message.attachments.withIndex()) {
                    addJsonObject {
                        put("id", index)
                        put("filename", attachment.filename)
                        put("description", attachment.description)
                    }
                }
            }
        }

        val form =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "payload_json",
                    null,
                    payload.toString().toRequestBody("application/json".toMediaType()),
                )
        for ((index, attachment) in message.attachments.withIndex()) {
            form.addFormDataPart(
                "files[$index]",
                attachment.filename,
                attachment.stream().use(InputStream::readAllBytes).toRequestBody(attachment.type),
            )
        }

        val request =
            Request.Builder()
                .addHeader("User-Agent", "Imperium (https://github.com/xpdustry/imperium, v$version)")
                .url(webhookConfig.discordWebhookUrl)
                .post(form.build())
                .build()

        http.newCall(request).await().use { response ->
            if (response.code / 100 != 2) {
                logger.error("Failed to send webhook message $message: ${response.body?.charStream()?.readText()}")
            }
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
