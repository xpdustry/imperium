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
package com.xpdustry.imperium.mindustry.control

import com.xpdustry.distributor.api.DistributorProvider
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.control.RestartMessage
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.await
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.common.webhook.WebhookMessage
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.onEvent
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.translation.server_restart_delay
import com.xpdustry.imperium.mindustry.translation.server_restart_game_over
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mindustry.Vars
import mindustry.game.EventType.GameOverEvent
import okhttp3.OkHttpClient
import okhttp3.Request

class RestartListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val webhook: WebhookMessageSender = instances.get()
    private val config: ImperiumConfig = instances.get()
    private val http: OkHttpClient = instances.get()
    private val version: ImperiumVersion = instances.get()
    private val restating = AtomicReference(false)
    private var download: AtomicReference<ImperiumVersion?> = AtomicReference()
    private val messenger = instances.get<Messenger>()
    private val application = instances.get<ImperiumApplication>()

    override fun onImperiumInit() {
        messenger.consumer<RestartMessage> {
            if (it.target == config.server.name) {
                prepareRestart("admin", it.immediate)
            }
        }
    }

    @TaskHandler(interval = 5L, unit = MindustryTimeUnit.MINUTES)
    fun onUpdateCheck() {
        if (!config.server.autoUpdate) {
            return
        }
        LOGGER.debug("Checking for updates...")
        ImperiumScope.IO.launch {
            http
                .newCall(
                    Request.Builder()
                        .url("https://api.github.com/repos/xpdustry/imperium/releases/latest")
                        .get()
                        .build())
                .await()
                .use { response ->
                    if (response.code != 200) {
                        LOGGER.error(
                            "Failed to check for updates (code={}): {}",
                            response.code,
                            response.body!!.string())
                        return@launch
                    }
                    val latest =
                        ImperiumVersion.parse(
                            Json.parseToJsonElement(response.body!!.string())
                                .jsonObject["tag_name"]!!
                                .jsonPrimitive
                                .content)
                    if (latest > version) {
                        download.set(latest)
                        prepareRestart("update", false)
                    }
                }
        }
    }

    private fun prepareRestart(reason: String, immediate: Boolean) {
        if (restating.getAndSet(true)) {
            return
        }
        val everyone = DistributorProvider.get().audienceProvider.everyone
        val gamemode = config.mindustry!!.gamemode
        if (immediate || Entities.getPlayers().isEmpty() || Vars.state.gameOver) {
            everyone.sendMessage(server_restart_delay(reason, 3.seconds))
            ImperiumScope.MAIN.launch { restart() }
        } else if (gamemode.pvp) {
            everyone.sendMessage(server_restart_game_over(reason))
            onEvent<GameOverEvent> {
                ImperiumScope.MAIN.launch {
                    delay(5.seconds)
                    restart()
                }
            }
        } else if (gamemode == MindustryGamemode.HUB) {
            everyone.sendMessage(server_restart_delay(reason, 10.seconds))
            ImperiumScope.MAIN.launch {
                delay(10.seconds)
                restart()
            }
        } else {
            everyone.sendMessage(server_restart_delay(reason, 5.minutes))
            ImperiumScope.MAIN.launch {
                delay(5.minutes)
                restart()
            }
        }
    }

    private suspend fun restart() {
        if (download.get() != null) {
            val target =
                Path(RestartListener::class.java.protectionDomain.codeSource.location.toURI().path)
            http
                .newCall(
                    Request.Builder()
                        .url(
                            "https://github.com/xpdustry/imperium/releases/download/v${download.get()!!}/imperium-mindustry.jar")
                        .get()
                        .build())
                .await()
                .use { response ->
                    try {
                        if (response.code != 200) {
                            throw IOException(
                                "Failed to download the latest version (code=${response.code}): ${response.message}")
                        }
                        val source = Files.createTempFile("imperium-mindustry", ".jar.tmp")
                        response.body!!.byteStream().use { input ->
                            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING)
                        }
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
                        LOGGER.info("Successfully downloaded version {}", download.get()!!)
                    } catch (e: Exception) {
                        LOGGER.error("Failed to download the latest version", e)
                        webhook.send(
                            WebhookMessage(
                                content = "@phinner, server failed to auto update: ${e.message}"))
                        runMindustryThread { application.exit(ExitStatus.EXIT) }
                        return
                    }
                }
        }
        runMindustryThread { application.exit(ExitStatus.RESTART) }
    }

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}
