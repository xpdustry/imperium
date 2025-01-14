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
package com.xpdustry.imperium.common.config

import com.google.common.net.InetAddresses
import com.sksamuel.hoplite.Secret
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.permission.Permission
import com.xpdustry.imperium.common.security.Identity
import java.awt.Color
import java.net.InetAddress
import java.net.URL
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val SUPPORTED_LANGUAGE =
    setOf(
        Locale.ENGLISH,
        Locale.FRENCH,
        Locale.GERMAN,
        Locale.forLanguageTag("es"),
        Locale.forLanguageTag("ru"),
        Locale.forLanguageTag("pl"),
        Locale.forLanguageTag("hr"),
    )

data class ImperiumConfig(
    val network: NetworkConfig = NetworkConfig(),
    val testing: Boolean = false,
    val database: DatabaseConfig = DatabaseConfig.H2(),
    val messenger: MessengerConfig = MessengerConfig.None,
    val server: ServerConfig = ServerConfig("unknown"),
    val language: Locale = Locale.ENGLISH,
    val supportedLanguages: Set<Locale> = SUPPORTED_LANGUAGE,
    val webhook: WebhookConfig = WebhookConfig.None,
    val discord: DiscordConfig = DiscordConfig(),
    val mindustry: MindustryConfig = MindustryConfig(),
    val webserver: WebserverConfig = WebserverConfig(),
    val storage: StorageConfig = StorageConfig.Local,
    val metrics: MetricConfig = MetricConfig.None,
)

data class NetworkConfig(
    val vpnDetection: VpnDetectionConfig = VpnDetectionConfig.None,
    val discoveryInterval: Duration = 10.seconds,
) {
    sealed interface VpnDetectionConfig {
        data object None : VpnDetectionConfig

        data class VpnApiIo(val vpnApiIoToken: Secret) : VpnDetectionConfig
    }
}

sealed interface DatabaseConfig {
    data class H2(val memory: Boolean = false, val database: String = "imperium") : DatabaseConfig

    data class MariaDB(
        val host: String = "localhost",
        val port: Short = 3306,
        val database: String = "imperium",
        val username: String = "root",
        val password: Secret = Secret("root"),
    ) : DatabaseConfig
}

sealed interface MessengerConfig {
    data object None : MessengerConfig

    data class RabbitMQ(
        val host: String = "localhost",
        val port: Int = 5672,
        val username: String = "guest",
        val password: Secret = Secret("guest"),
        val ssl: Boolean = false,
    ) : MessengerConfig
}

data class ServerConfig(val name: String, val displayName: String = name.capitalize()) {
    val identity: Identity.Server
        get() = Identity.Server(name)

    init {
        require(NAME_REGEX.matches(name)) { "Server name must match regex ${NAME_REGEX.pattern}" }
    }

    companion object {
        private val NAME_REGEX = Regex("^[a-z0-9](-?[a-z0-9])+\$")
    }
}

sealed interface WebhookConfig {
    data object None : WebhookConfig

    data class Discord(val discordWebhookUrl: URL) : WebhookConfig
}

// TODO Cleanup roles (ranks, permission, special) listing and lookup
data class DiscordConfig(
    val token: Secret = Secret(""),
    val categories: Categories = Categories(),
    val channels: Channels = Channels(),
    val ranks2roles: Map<Rank, Long> = emptyMap(),
    val permissions2roles: Map<Permission, Long> = emptyMap(),
    val achievements2roles: Map<Achievement, Long> = emptyMap(),
    val mindustryVersion: String = "145",
    val globalCommands: Boolean = false,
    val alertsRole: Long? = null,
) {
    val roles2ranks: Map<Long, Rank> = ranks2roles.entries.associate { (key, value) -> value to key }

    init {
        require(ranks2roles.size == roles2ranks.size) { "some ranks have a shared role id" }
    }

    data class Categories(val liveChat: Long = 0)

    data class Channels(val notifications: Long = 0, val maps: Long = 0, val reports: Long = 0)
}

data class MindustryConfig(
    val gamemode: MindustryGamemode = MindustryGamemode.SURVIVAL,
    val quotes: List<String> = listOf("Bonjour", "The best mindustry server of all time"),
    val hub: Hub = Hub(),
    val history: History = History(),
    val color: Color = Color.WHITE,
    val world: World = World(),
    val security: Security = Security(),
    val tipsDelay: Duration = 5.minutes,
) {
    data class History(
        val tileEntriesLimit: Int = 20,
        val playerEntriesLimit: Int = 200,
        val doubleClickDelay: Duration = 200.milliseconds,
        val heatMapRadius: Int = 15,
    )

    data class World(
        val maxExcavateSize: Int = 1024,
        val excavationTilePrice: Int = 10,
        val excavationItem: String = "blast-compound",
        val coreDamageAlertDelay: Duration = 10.seconds,
        val displayCoreId: Boolean = true,
        val displayResourceTracker: Boolean = true,
        val explosiveDamageAlertDelay: Duration = 15.seconds,
    )

    data class Security(
        val gatekeeper: Boolean = true,
        val imageProcessingDelay: Duration = 3.seconds,
        val griefingThreshold: Float = 80F,
    )

    data class Hub(
        val preventPlayerActions: Boolean = true,
        val errorFontSize: Float = 2F,
        val overlays: List<Overlay> = emptyList(),
    ) {
        data class Overlay(
            val text: String,
            val offsetX: Float = 0F,
            val offsetY: Float = 0F,
            val fontSize: Float = 2F,
            val outline: Boolean = false,
            val background: Boolean = false,
        )
    }
}

data class WebserverConfig(val port: Int = 8080, val host: InetAddress = InetAddresses.forString("0.0.0.0"))

sealed interface StorageConfig {
    data object Local : StorageConfig

    data class Minio(
        val host: String = "localhost",
        val port: Int = 9000,
        val secure: Boolean = false,
        val accessKey: Secret = Secret("minioadmin"),
        val secretKey: Secret = Secret("minioadmin"),
        val bucket: String = "imperium",
    ) : StorageConfig
}

sealed interface MetricConfig {
    data object None : MetricConfig

    data class InfluxDB(
        val endpoint: URL,
        val token: Secret,
        val organization: String,
        val bucket: String = "imperium",
        val interval: Duration = 10.seconds,
    ) : MetricConfig
}
