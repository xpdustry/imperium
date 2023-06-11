/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.common.network

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalCause
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.message.Messenger
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.slf4j.LoggerFactory
import reactor.core.Disposable
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private val logger = LoggerFactory.getLogger(SimpleDiscovery::class.java)

// TODO: Test this discovery system since it uses monos and stuff
class SimpleDiscovery @Inject constructor(
    private val messenger: Messenger,
    private val infoProvider: Provider<ServerInfo>,
) : Discovery, FoundationListener {

    private val _servers: Cache<String, ServerInfo> = CacheBuilder.newBuilder()
        .expireAfterWrite(45L, TimeUnit.SECONDS)
        .removalListener(DiscoveryRemovalListener())
        .build()

    private var heartbeatTask: Disposable? = null

    override fun onFoundationInit() {
        messenger.subscribe(DiscoveryMessage::class).doOnNext {
            if (it.type === DiscoveryMessage.Type.DISCOVER) {
                logger.trace("Received discovery message from {}", it.info.name)
                this._servers.put(it.info.name, it.info)
            } else if (it.type === DiscoveryMessage.Type.UN_DISCOVER) {
                this._servers.invalidate(it.info.name)
                logger.debug("Undiscovered server {}", it.info.name)
            } else {
                logger.warn("Received unknown discovery message type {}", it.type)
            }
        }
            .subscribe()

        Mono.delay(Duration.ofSeconds(Random.Default.nextLong(5)))
            .then(Mono.fromRunnable<Void>(this::heartbeat))
            .subscribe()
    }

    override fun heartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask?.dispose()
        }
        heartbeatTask = sendDiscovery(DiscoveryMessage.Type.DISCOVER)
            .onErrorResume { Mono.empty() }
            .delayElement(Duration.ofSeconds(30))
            .then(Mono.fromRunnable<Void>(this::heartbeat))
            .subscribe()
    }

    private fun sendDiscovery(type: DiscoveryMessage.Type): Mono<Void> {
        logger.trace("Sending {} discovery message", type.name.lowercase(Locale.ROOT))
        return messenger.publish(DiscoveryMessage(infoProvider.get(), type))
    }

    override fun onFoundationExit() {
        heartbeatTask?.dispose()
        sendDiscovery(DiscoveryMessage.Type.UN_DISCOVER).subscribe()
    }

    override val servers: Map<String, ServerInfo>
        get() = this._servers.asMap()

    private inner class DiscoveryRemovalListener : RemovalListener<String, ServerInfo> {
        override fun onRemoval(notification: RemovalNotification<String, ServerInfo>) {
            if (notification.cause === RemovalCause.EXPIRED) {
                logger.debug("Server {} has timeout.", notification.key)
            }
        }
    }
}
