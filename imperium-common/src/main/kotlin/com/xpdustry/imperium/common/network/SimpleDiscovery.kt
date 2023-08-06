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
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.onErrorResumeEmpty
import reactor.core.Disposable
import reactor.core.publisher.Mono
import reactor.core.publisher.SignalType
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.random.Random

class SimpleDiscovery(
    private val messenger: Messenger,
    private val metadata: ImperiumMetadata,
    private val mindustryServerProvider: Supplier<MindustryServerInfo?>,
) : Discovery, ImperiumApplication.Listener {

    override val servers: List<ServerInfo> get() = this._servers.asMap().values.toList()

    private val _servers: Cache<String, ServerInfo> = CacheBuilder.newBuilder()
        .expireAfterWrite(45L, TimeUnit.SECONDS)
        .removalListener(DiscoveryRemovalListener())
        .build()

    private var heartbeatTask: Disposable? = null

    override fun onImperiumInit() {
        logger.debug("Starting discovery as {}", metadata.identifier)

        messenger.on(DiscoveryMessage::class).subscribe {
            if (it.type === DiscoveryMessage.Type.DISCOVER) {
                logger.trace("Received discovery message from {}", it.info.metadata.identifier)
                this._servers.put(it.info.metadata.identifier, it.info)
            } else if (it.type === DiscoveryMessage.Type.UN_DISCOVER) {
                this._servers.invalidate(it.info.metadata.identifier)
                logger.debug("Undiscovered server {}", it.info.metadata.identifier)
            } else {
                logger.warn("Received unknown discovery message type {}", it.type)
            }
        }

        Mono.delay(Duration.ofSeconds(Random.nextLong(5)))
            .doFinally { heartbeat() }
            .subscribe()
    }

    override fun heartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask?.dispose()
        }
        heartbeatTask = sendDiscovery(DiscoveryMessage.Type.DISCOVER)
            .onErrorResumeEmpty { logger.error("Failed to send discovery message", it) }
            // TODO Make delay configurable
            .delaySubscription(Duration.ofSeconds(5L))
            .doFinally { if (it == SignalType.ON_COMPLETE) heartbeat() }
            .subscribe()
    }

    private fun sendDiscovery(type: DiscoveryMessage.Type): Mono<Void> {
        logger.trace("Sending {} discovery message", type.name.lowercase(Locale.ROOT))
        return messenger.publish(DiscoveryMessage(ServerInfo(metadata, mindustryServerProvider.get()), type))
    }

    override fun onImperiumExit() {
        heartbeatTask?.dispose()
        sendDiscovery(DiscoveryMessage.Type.UN_DISCOVER)
            .onErrorResumeEmpty { logger.error("Failed to send un-discovery message", it) }
            .block()
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
