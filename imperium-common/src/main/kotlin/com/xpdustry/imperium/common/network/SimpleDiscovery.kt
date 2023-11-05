/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.imperium.common.network

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalCause
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.application.ImperiumMetadata
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.LoggerDelegate
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SimpleDiscovery(
    private val messenger: Messenger,
    private val metadata: ImperiumMetadata,
    private val mindustryServerProvider: Supplier<MindustryServerInfo?>,
    private val config: ImperiumConfig,
) : Discovery, ImperiumApplication.Listener {

    override val servers: List<ServerInfo>
        get() = this._servers.asMap().values.toList()

    private val _servers: Cache<String, ServerInfo> =
        CacheBuilder.newBuilder()
            .expireAfterWrite(45L, TimeUnit.SECONDS)
            .removalListener(DiscoveryRemovalListener())
            .build()

    private lateinit var heartbeatJob: Job

    override fun onImperiumInit() {
        logger.debug("Starting discovery as {}", metadata.identifier)

        messenger.consumer<DiscoveryMessage> {
            if (it.info.serverName == config.server.name) {
                logger.warn("Received discovery message from another server with the same name.")
            } else if (it.type === DiscoveryMessage.Type.DISCOVER) {
                logger.trace("Received discovery message from {}", it.info.metadata.identifier)
                this._servers.put(it.info.metadata.identifier.toString(), it.info)
            } else if (it.type === DiscoveryMessage.Type.UN_DISCOVER) {
                this._servers.invalidate(it.info.metadata.identifier.toString())
                logger.debug("Undiscovered server {}", it.info.metadata.identifier)
            }
        }

        heartbeatJob =
            ImperiumScope.MAIN.launch {
                delay(Random.nextLong(5).seconds)
                while (isActive) {
                    sendDiscovery(DiscoveryMessage.Type.DISCOVER)
                    delay(5.seconds)
                }
            }
    }

    override fun onImperiumExit() = runBlocking {
        heartbeatJob.cancelAndJoin()
        sendDiscovery(DiscoveryMessage.Type.UN_DISCOVER)
    }

    override fun heartbeat() =
        runBlocking(ImperiumScope.MAIN.coroutineContext) {
            sendDiscovery(DiscoveryMessage.Type.DISCOVER)
        }

    private suspend fun sendDiscovery(type: DiscoveryMessage.Type) {
        logger.trace("Sending {} discovery message", type.name)
        messenger.publish(
            DiscoveryMessage(
                ServerInfo(config.server.name, metadata, mindustryServerProvider.get()), type))
    }

    private inner class DiscoveryRemovalListener : RemovalListener<String, ServerInfo> {
        override fun onRemoval(notification: RemovalNotification<String, ServerInfo>) {
            if (notification.cause === RemovalCause.EXPIRED) {
                logger.debug("Server {} has timeout.", notification.key)
            }
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
