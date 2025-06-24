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
package com.xpdustry.imperium.common

import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.SimpleAccountManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.bridge.RequestingPlayerTracker
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ImperiumConfigProvider
import com.xpdustry.imperium.common.config.MessengerConfig
import com.xpdustry.imperium.common.config.MetricConfig
import com.xpdustry.imperium.common.config.NetworkConfig
import com.xpdustry.imperium.common.config.StorageConfig
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.content.SimpleMindustryMapManager
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.ImperiumC6B36Codec
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.database.SimpleSQLProvider
import com.xpdustry.imperium.common.inject.MutableInstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.provider
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.NoopMessenger
import com.xpdustry.imperium.common.message.RabbitmqMessenger
import com.xpdustry.imperium.common.metrics.InfluxDBRegistry
import com.xpdustry.imperium.common.metrics.MetricsRegistry
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.network.SimpleDiscovery
import com.xpdustry.imperium.common.network.VpnApiIoDetection
import com.xpdustry.imperium.common.network.VpnDetection
import com.xpdustry.imperium.common.security.AddressWhitelist
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.SimpleAddressWhitelist
import com.xpdustry.imperium.common.security.SimplePunishmentManager
import com.xpdustry.imperium.common.storage.LocalStorageBucket
import com.xpdustry.imperium.common.storage.MinioStorageBucket
import com.xpdustry.imperium.common.storage.StorageBucket
import com.xpdustry.imperium.common.time.SimpleTimeRenderer
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.SimpleUserManager
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.imperium.common.webhook.WebhookMessageSenderImpl
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

fun MutableInstanceManager.registerCommonModule() {
    provider(ImperiumConfigProvider)

    provider<Discovery> { SimpleDiscovery(get(), get("discovery"), get()) }

    provider<VpnDetection> {
        when (val config = get<ImperiumConfig>().network.vpnDetection) {
            is NetworkConfig.VpnDetectionConfig.None -> VpnDetection.Noop
            is NetworkConfig.VpnDetectionConfig.VpnApiIo -> VpnApiIoDetection(config, get())
        }
    }

    provider<Messenger> {
        when (get<ImperiumConfig>().messenger) {
            is MessengerConfig.RabbitMQ -> RabbitmqMessenger(get())
            is MessengerConfig.None -> NoopMessenger()
        }
    }

    provider<SQLProvider> { SimpleSQLProvider(get<ImperiumConfig>().database, get("directory")) }

    provider<AccountManager> { SimpleAccountManager(get(), get(), get()) }

    provider<MindustryMapManager> { SimpleMindustryMapManager(get(), get(), get(), get()) }

    provider<PunishmentManager> { SimplePunishmentManager(get(), get(), get(), get()) }

    provider<UserManager> { SimpleUserManager(get(), get()) }

    provider<OkHttpClient> {
        OkHttpClient.Builder()
            .connectTimeout(20.seconds.toJavaDuration())
            .connectTimeout(20.seconds.toJavaDuration())
            .readTimeout(20.seconds.toJavaDuration())
            .writeTimeout(20.seconds.toJavaDuration())
            .dispatcher(
                Dispatcher(
                    // The default executor blocks the exit in Mindustry
                    Executors.newFixedThreadPool(
                        Runtime.getRuntime().availableProcessors(),
                        ThreadFactoryBuilder().setDaemon(true).setNameFormat("imperium-okhttp-%d").build(),
                    )
                )
            )
            .build()
    }

    provider<IdentifierCodec> { ImperiumC6B36Codec }

    provider<Supplier<Discovery.Data>>("discovery") { Supplier { Discovery.Data.Unknown } }

    provider<ImperiumVersion> { ImperiumVersion(1, 1, 1) }

    provider<PlayerTracker> { RequestingPlayerTracker(get()) }

    provider<TimeRenderer> { SimpleTimeRenderer(get()) }

    provider<WebhookMessageSender> { WebhookMessageSenderImpl(get(), get(), get()) }

    provider<AddressWhitelist> { SimpleAddressWhitelist(get()) }

    provider<Executor>("main") { MoreExecutors.directExecutor() }

    provider<StorageBucket> {
        when (val config = get<ImperiumConfig>().storage) {
            is StorageConfig.Local -> LocalStorageBucket(get<Path>("directory").resolve("storage"))
            is StorageConfig.Minio -> MinioStorageBucket(config, get())
        }
    }

    provider<MetricsRegistry> {
        val config = get<ImperiumConfig>()
        when (config.metrics) {
            is MetricConfig.InfluxDB -> InfluxDBRegistry(config.server, config.metrics, get())
            is MetricConfig.None -> MetricsRegistry.None
        }
    }
}

fun MutableInstanceManager.registerApplication(application: ImperiumApplication) {
    provider<ImperiumApplication> { application }
}
