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
import com.xpdustry.imperium.common.webhook.WebhookMessage
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.nohorny.image.NoHornyResult
import com.xpdustry.nohorny.image.analyzer.ImageAnalyzerEvent
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
    fun onImageLogicAnalyzer(event: ImageAnalyzerEvent) =
        ImperiumScope.MAIN.launch {
            when (event.result.rating) {
                NoHornyResult.Rating.SAFE -> Unit
                NoHornyResult.Rating.WARNING -> {
                    logger.debug(
                        "Cluster in rect ({}, {}, {}, {}) is possibly unsafe.",
                        event.group.x,
                        event.group.y,
                        event.group.w,
                        event.group.h,
                    )

                    webhook.send(
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
                                    for ((entry, percent) in event.result.details) {
                                        appendLine("- ${entry.name}: ${"%.1f %%".format(percent * 100)}")
                                    }
                                },
                            attachments = listOf(event.image.toUnsafeAttachment()),
                        )
                    )
                }
                NoHornyResult.Rating.UNSAFE -> {
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
                        WebhookMessage(
                            content =
                                buildString {
                                    appendLine("**NSFW image detected**")
                                    appendLine("Related to punishment `${codec.encode(punishment)}`")
                                    for ((entry, percent) in event.result.details) {
                                        appendLine("- ${entry.name}: ${"%.1f %%".format(percent * 100)}")
                                    }
                                },
                            attachments = listOf(event.image.toUnsafeAttachment()),
                        )
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
