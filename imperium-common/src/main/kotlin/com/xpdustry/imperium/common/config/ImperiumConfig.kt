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

import com.sksamuel.hoplite.Secret
import com.xpdustry.imperium.common.account.Account
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.security.Identity
import java.awt.Color
import java.net.URL
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class ImperiumConfig(
    val network: NetworkConfig = NetworkConfig(),
    val translator: TranslatorConfig = TranslatorConfig.None,
    val database: DatabaseConfig = DatabaseConfig.SQL(),
    val messenger: MessengerConfig = MessengerConfig.None,
    val server: ServerConfig,
    val generatorId: Int = 0,
    val language: Locale = Locale.ENGLISH,
    val supportedLanguages: Set<Locale> = setOf(Locale.ENGLISH, Locale.FRENCH),
    val webhook: WebhookConfig = WebhookConfig.None,
    val discord: DiscordConfig? = null,
    val mindustry: MindustryConfig? = null
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

sealed interface TranslatorConfig {
    data object None : TranslatorConfig

    data class DeepL(val token: Secret) : TranslatorConfig
}

sealed interface DatabaseConfig {
    data class SQL(
        val host: String = "./database.h2;MODE=MYSQL",
        val port: Short = 3306,
        val database: String = "imperium",
        val username: String = "root",
        val password: Secret = Secret("root"),
        val poolMin: Int = 2,
        val poolMax: Int = 8,
        val type: Type = Type.H2
    ) : DatabaseConfig {
        init {
            require(poolMin > 0) { "poolMin can't be below 1, got $poolMin" }
            require(poolMax >= poolMin) { "poolMax can't be lower than poolMin, got $poolMax" }
        }

        enum class Type(val driver: String) {
            MARIADB("org.mariadb.jdbc.Driver"),
            H2("org.h2.Driver")
        }
    }
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

data class DiscordConfig(
    val token: Secret,
    val categories: Categories,
    val channels: Channels,
    val ranks2roles: Map<Rank, Long> = emptyMap(),
    val achievements2roles: Map<Account.Achievement, Long> = emptyMap(),
    val mindustryVersion: String = "145",
    val globalCommands: Boolean = false
) {
    val roles2ranks: Map<Long, Rank> =
        ranks2roles.entries.associate { (key, value) -> value to key }

    init {
        require(ranks2roles.size == roles2ranks.size) { "some ranks have a shared role id" }
    }

    data class Categories(
        val liveChat: Long,
    )

    data class Channels(
        val notifications: Long,
        val maps: Long,
        val reports: Long,
    )
}

data class MindustryConfig(
    val gamemode: MindustryGamemode,
    val quotes: List<String> = listOf("Bonjour", "The best mindustry server of all time"),
    val hub: Hub = Hub(),
    val history: History = History(),
    val color: Color = Color.WHITE,
    val world: World = World(),
    val security: Security = Security(),
    val templates: Templates = Templates(),
    val tipsDelay: Duration = 5.minutes,
    val tips: List<String> = listOf("discord", "rules")
) {
    data class History(
        val tileEntriesLimit: Int = 20,
        val playerEntriesLimit: Int = 200,
        val doubleClickDelay: Duration = 200.milliseconds
    )

    data class World(
        val maxExcavateSize: Int = 1024,
        val excavationTilePrice: Int = 10,
        val excavationItem: String = "blast-compound",
        val coreDamageAlertDelay: Duration = 10.seconds,
        val displayCoreId: Boolean = true,
        val displayResourceTracker: Boolean = true,
    )

    data class Security(
        val gatekeeper: Boolean = true,
        val imageProcessingDelay: Duration = 3.seconds,
    )

    data class Templates(
        val chatPrefix: String = "<%prefix%>",
        val chatFormat: String =
            "[accent]<[white]%subject_playtime:chaotic%[accent]> [%subject_color:hex%]%subject_name:display% [accent]>[white]",
        val playerName: String =
            "[accent]<[white]%subject_playtime:chaotic%[accent]> [%subject_color:hex%]%subject_name:display%",
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
