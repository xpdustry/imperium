// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.image.ImageFormat
import com.xpdustry.imperium.common.image.inputStream
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.common.webhook.WebhookChannel
import com.xpdustry.imperium.common.webhook.WebhookMessage
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.nohorny.client.ClassificationEvent
import com.xpdustry.nohorny.common.MindustryImageRenderer
import com.xpdustry.nohorny.common.Rating
import java.awt.image.BufferedImage
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType

class NoHornyListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val punishments = instances.get<PunishmentManager>()
    private val config = instances.get<ImperiumConfig>()
    private val webhook = instances.get<WebhookMessageSender>()
    private val codec = instances.get<IdentifierCodec>()

    @EventHandler
    fun onImageLogicAnalyzer(event: ClassificationEvent) =
        ImperiumScope.MAIN.launch {
            when (event.rating) {
                Rating.SAFE -> Unit
                Rating.WARN -> {
                    logger.debug(
                        "Cluster in rect ({}, {}, {}, {}) is possibly unsafe.",
                        event.group.x,
                        event.group.y,
                        event.group.w,
                        event.group.h,
                    )

                    webhook.send(
                        WebhookChannel.NOHORNY,
                        WebhookMessage(
                            content =
                                buildString {
                                    appendLine("**Possible NSFW image detected**")
                                    append("Located at ${event.group.x}, ${event.group.y}")
                                    val id = event.author?.uuid?.let { users.findByUuid(it) }?.id?.let(codec::encode)
                                    if (id != null) {
                                        append(" by user `$id`")
                                    }
                                    appendLine()
                                },
                            attachments = listOf(MindustryImageRenderer.render(event.group).toUnsafeAttachment()),
                        ),
                    )
                }
                Rating.NSFW -> {
                    logger.info(
                        "Cluster in rect ({}, {}, {}, {}) is unsafe. Destroying blocks.",
                        event.group.x,
                        event.group.y,
                        event.group.w,
                        event.group.h,
                    )

                    val user = event.author?.uuid?.let { users.findByUuid(it) } ?: return@launch
                    val punishment =
                        punishments.punish(
                            config.server.identity,
                            user.id,
                            "Placing NSFW image",
                            Punishment.Type.BAN,
                            30.days,
                        )

                    webhook.send(
                        WebhookChannel.NOHORNY,
                        WebhookMessage(
                            content =
                                buildString {
                                    appendLine("**NSFW image detected**")
                                    appendLine("Related to punishment `${codec.encode(punishment)}`")
                                },
                            attachments = listOf(MindustryImageRenderer.render(event.group).toUnsafeAttachment()),
                        ),
                    )
                }
            }
        }

    private fun BufferedImage.toUnsafeAttachment() =
        WebhookMessage.Attachment("SPOILER_image.jpg", "NSFW image", "image/jpeg".toMediaType()) {
            inputStream(ImageFormat.JPG)
        }

    companion object {
        private val logger by LoggerDelegate()
    }
}
