// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.webhook

import java.awt.Color
import java.io.InputStream
import okhttp3.MediaType

data class WebhookMessage(
    val content: String? = "",
    val embeds: List<Embed> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
) {
    data class Embed(
        val title: String? = null,
        val thumbnail: Media? = null,
        val description: String? = null,
        val color: Color? = null,
        val image: Media? = null,
    ) {
        data class Media(val url: String)
    }

    data class Attachment(
        val filename: String,
        val description: String,
        val type: MediaType,
        val stream: () -> InputStream,
    )
}
