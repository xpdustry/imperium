// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common

import com.google.common.util.concurrent.MoreExecutors
import com.xpdustry.imperium.common.account.AccountAchievementService
import com.xpdustry.imperium.common.account.AccountMetadataService
import com.xpdustry.imperium.common.account.AccountService
import com.xpdustry.imperium.common.account.MindustrySessionService
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.bridge.RequestingPlayerTracker
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ImperiumConfigProvider
import com.xpdustry.imperium.common.config.MessengerConfig
import com.xpdustry.imperium.common.config.MetricConfig
import com.xpdustry.imperium.common.config.NetworkConfig
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.content.SimpleMindustryMapManager
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.ImperiumC6B36Codec
import com.xpdustry.imperium.common.database.SQLDatabase
import com.xpdustry.imperium.common.database.SQLDatabaseImpl
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.database.SimpleSQLProvider
import com.xpdustry.imperium.common.dependency.DependencyService
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.SQLMessageService
import com.xpdustry.imperium.common.metrics.MetricsRegistry
import com.xpdustry.imperium.common.metrics.SQLMetricsRegistry
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.network.DiscoveryDataSupplier
import com.xpdustry.imperium.common.network.SimpleDiscovery
import com.xpdustry.imperium.common.network.VpnApiIoDetection
import com.xpdustry.imperium.common.network.VpnDetection
import com.xpdustry.imperium.common.security.AddressWhitelist
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.SimpleAddressWhitelist
import com.xpdustry.imperium.common.security.SimplePunishmentManager
import com.xpdustry.imperium.common.time.SimpleTimeRenderer
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.SimpleUserManager
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.imperium.common.webhook.WebhookMessageSenderImpl
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

fun DependencyService.Binder.registerCommonModule() {
    // Configuration.
    bindToFunc<ImperiumConfig>(ImperiumConfigProvider::loadConfig)
    bindToFunc<DatabaseConfig>(::getDatabaseConfig)

    // Network.
    bindToImpl<Discovery, SimpleDiscovery>()
    bindToFunc<DiscoveryDataSupplier>(::createUnknownDiscoveryDataSupplier)
    bindToFunc<VpnDetection>(::createVpnDetection)
    bindToFunc<OkHttpClient>(::createOkHttpClient)

    // Persistence.
    bindToImpl<SQLProvider, SimpleSQLProvider>()
    bindToImpl<SQLDatabase, SQLDatabaseImpl>()

    // Domain services.
    bindToImpl<AccountService, AccountService>()
    bindToImpl<AccountAchievementService, AccountAchievementService>()
    bindToImpl<AccountMetadataService, AccountMetadataService>()
    bindToImpl<MindustrySessionService, MindustrySessionService>()
    bindToImpl<MindustryMapManager, SimpleMindustryMapManager>()
    bindToImpl<PunishmentManager, SimplePunishmentManager>()
    bindToImpl<UserManager, SimpleUserManager>()
    bindToImpl<AddressWhitelist, SimpleAddressWhitelist>()

    // Shared services.
    bindToFunc<IdentifierCodec>(::createIdentifierCodec)
    bindToImpl<PlayerTracker, RequestingPlayerTracker>()
    bindToImpl<TimeRenderer, SimpleTimeRenderer>()
    bindToImpl<WebhookMessageSender, WebhookMessageSenderImpl>()
    bindToFunc<MessageService>(::createMessageService)
    bindToFunc<MetricsRegistry>(::createMetricsRegistry)

    // Runtime.
    bindToFunc<ImperiumVersion>(::createImperiumVersion)
    bindToFunc<Executor>(::createMainExecutor, "main")
}

private fun getDatabaseConfig(config: ImperiumConfig): DatabaseConfig = config.database

private fun createVpnDetection(config: ImperiumConfig, http: OkHttpClient): VpnDetection =
    when (val vpn = config.network.vpnDetection) {
        is NetworkConfig.VpnDetectionConfig.None -> VpnDetection.Noop
        is NetworkConfig.VpnDetectionConfig.VpnApiIo -> VpnApiIoDetection(vpn, http)
    }

private fun createOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(20.seconds.toJavaDuration())
        .connectTimeout(20.seconds.toJavaDuration())
        .readTimeout(20.seconds.toJavaDuration())
        .writeTimeout(20.seconds.toJavaDuration())
        .dispatcher(
            Dispatcher(Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("imperium-okttp-", 0).factory()))
        )
        .build()

private fun createIdentifierCodec(): IdentifierCodec = ImperiumC6B36Codec

private fun createUnknownDiscoveryDataSupplier(): DiscoveryDataSupplier = DiscoveryDataSupplier {
    Discovery.Data.Unknown
}

private fun createImperiumVersion(): ImperiumVersion = ImperiumVersion(1, 1, 1)

private fun createMainExecutor(): Executor = MoreExecutors.directExecutor()

private fun createMetricsRegistry(config: ImperiumConfig, database: SQLDatabase): MetricsRegistry =
    when (val metrics = config.metrics) {
        is MetricConfig.SQL -> SQLMetricsRegistry(config.server, metrics, database)
        is MetricConfig.None -> MetricsRegistry.None
    }

private fun createMessageService(config: ImperiumConfig, database: SQLDatabase): MessageService =
    when (config.messenger) {
        MessengerConfig.Noop -> MessageService.Noop
        MessengerConfig.SQL -> SQLMessageService(database, config, ImperiumScope.IO)
    }
