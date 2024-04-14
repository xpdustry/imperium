/*
 * Imperium, the software collection powering the Xpdustry network.
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
 *
 */
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.annotation.method.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ServerConfig
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
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.nohorny.NoHornyImage
import com.xpdustry.nohorny.analyzer.ImageAnalyzer
import com.xpdustry.nohorny.analyzer.ImageAnalyzerEvent
import java.awt.image.BufferedImage
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.content.Blocks
import okhttp3.MediaType.Companion.toMediaType

class LogicImageListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val punishments = instances.get<PunishmentManager>()
    private val config = instances.get<ServerConfig.Mindustry>()
    private val webhook = instances.get<WebhookMessageSender>()

    @EventHandler
    fun onLogicAnalyzerEvent(event: ImageAnalyzerEvent) =
        ImperiumScope.MAIN.launch {
            when (event.result.rating) {
                ImageAnalyzer.Rating.SAFE -> {
                    logger.debug(
                        "Cluster {} in rect ({}, {}, {}, {}) is safe",
                        event.cluster.identifier,
                        event.cluster.x,
                        event.cluster.y,
                        event.cluster.w,
                        event.cluster.h)
                }
                ImageAnalyzer.Rating.WARNING -> {
                    logger.debug(
                        "Cluster {} in rect ({}, {}, {}, {}) is possibly unsafe.",
                        event.cluster.identifier,
                        event.cluster.x,
                        event.cluster.y,
                        event.cluster.w,
                        event.cluster.h)

                    webhook.send(
                        WebhookMessage(
                            content =
                                buildString {
                                    appendLine("**Possible NSFW image detected**")
                                    appendLine("Located at ${event.cluster.x}, ${event.cluster.y}")
                                    for ((entry, percent) in event.result.details) {
                                        appendLine(
                                            "- ${entry.name}: ${"%.1f %%".format(percent * 100)}")
                                    }
                                },
                            attachments = listOf(event.image.toUnsafeAttachment())))
                }
                ImageAnalyzer.Rating.UNSAFE -> {
                    logger.info(
                        "Cluster {} in rect ({}, {}, {}, {}) is unsafe. Destroying blocks.",
                        event.cluster.identifier,
                        event.cluster.x,
                        event.cluster.y,
                        event.cluster.w,
                        event.cluster.h)

                    runMindustryThread {
                        for (block in event.cluster.blocks) {
                            Vars.world.tile(block.x, block.y)?.setNet(Blocks.air)
                            val payload = block.payload
                            if (payload is NoHornyImage.Display) {
                                for (processor in payload.processors.keys) {
                                    Vars.world.tile(processor.x, processor.y)?.setNet(Blocks.air)
                                }
                            }
                        }
                    }

                    val user = event.author?.uuid?.let { users.findByUuid(it) } ?: return@launch
                    val punishment =
                        punishments.punish(
                            config.identity,
                            user.snowflake,
                            "Placing NSFW image",
                            Punishment.Type.BAN,
                            30.days)

                    webhook.send(
                        WebhookMessage(
                            content =
                                buildString {
                                    appendLine("**NSFW image detected**")
                                    appendLine("Related to punishment $punishment")
                                    for ((entry, percent) in event.result.details) {
                                        appendLine(
                                            "- ${entry.name}: ${"%.1f %%".format(percent * 100)}")
                                    }
                                },
                            attachments = listOf(event.image.toUnsafeAttachment())))
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
