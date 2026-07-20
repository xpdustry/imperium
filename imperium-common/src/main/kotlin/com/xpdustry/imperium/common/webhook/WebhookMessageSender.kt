// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.webhook

import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.await
import com.xpdustry.imperium.common.version.ImperiumVersion
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
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

    suspend fun send(message: WebhookMessage)
}

@Inject
class WebhookMessageSenderImpl(
    private val config: ImperiumConfig,
    private val http: OkHttpClient,
    private val version: ImperiumVersion,
) : WebhookMessageSender {
    override suspend fun send(message: WebhookMessage) {
        if (config.webhook == null) {
            return
        }
        withContext(Dispatchers.IO) {
            try {
                send0(message)
            } catch (e: Throwable) {
                logger.error("Fatal error sending webhook message", e)
            }
        }
    }

    private suspend fun send0(message: WebhookMessage) {
        if (config.webhook == null) {
            return
        }
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
        // TODO Use temp files or pipes
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
                .url(config.webhook.discordWebhookUrl)
                .post(form.build())
                .build()

        http.newCall(request).await().use { response ->
            if (response.code != 204) {
                logger.error(
                    "Failed to send webhook message $message (status-code=${response.code}, body=${response.body.string()})"
                )
            }
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
