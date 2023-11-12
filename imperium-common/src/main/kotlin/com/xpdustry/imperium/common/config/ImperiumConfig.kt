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
package com.xpdustry.imperium.common.config

import com.sksamuel.hoplite.Secret
import com.xpdustry.imperium.common.account.Role
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.security.Identity
import java.awt.Color
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class ImperiumConfig(
    val network: NetworkConfig = NetworkConfig(),
    val translator: TranslatorConfig = TranslatorConfig.None,
    val database: DatabaseConfig = DatabaseConfig.Mongo(),
    val messenger: MessengerConfig = MessengerConfig.RabbitMQ(),
    val server: ServerConfig = ServerConfig.None,
    val storage: StorageConfig = StorageConfig.Minio(),
    val imageAnalysis: ImageAnalysisConfig = ImageAnalysisConfig.None,
    val generatorId: Int = 0,
    val language: Locale = Locale.ENGLISH,
    val supportedLanguages: Set<Locale> = setOf(Locale.ENGLISH, Locale.FRENCH),
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
    data class Mongo(
        val host: String = "localhost",
        val port: Int = 27017,
        val username: String = "",
        val password: Secret = Secret(""),
        val ssl: Boolean = false,
        val database: String = "imperium",
        val authDatabase: String = "admin",
    ) : DatabaseConfig
}

sealed interface MessengerConfig {
    data class RabbitMQ(
        val host: String = "localhost",
        val port: Int = 5672,
        val username: String = "guest",
        val password: Secret = Secret("guest"),
        val ssl: Boolean = false,
    ) : MessengerConfig
}

sealed interface ServerConfig {
    val name: String
    val displayName: String
        get() = name.capitalize()

    val identity: Identity.Server
        get() = Identity.Server(name)

    data object None : ServerConfig {
        override val name: String = "none"
    }

    data class Mindustry(
        override val name: String,
        val gamemode: MindustryGamemode,
        override val displayName: String = name.capitalize(),
        val quotes: List<String> = listOf("Bonjour", "The best mindustry server of all time"),
        val hub: Hub = Hub(),
        val history: History = History(),
        val color: Color = Color.WHITE,
        val world: World = World(),
        val security: Security = Security(),
        val templates: Templates = Templates(),
    ) : ServerConfig {
        init {
            require(name != "discord") { "Mindustry Server name cannot be discord" }
            require(NAME_REGEX.matches(name)) {
                "Mindustry Server name must match regex ${NAME_REGEX.pattern}"
            }
        }

        data class History(
            val tileEntriesLimit: Int = 10,
            val playerEntriesLimit: Int = 200,
        )

        data class World(
            val maxExcavateSize: Int = 64,
            val coreDamageAlertDelay: Duration = 10.seconds,
            val displayCoreId: Boolean = true,
        )

        data class Security(
            val gatekeeper: Boolean = true,
            val imageProcessingDelay: Duration = 3.seconds,
        )

        data class Templates(
            val chatPrefix: String = "<%prefix%>",
            val chatFormat: String =
                "[accent]<[white]%subject_playtime:chaotic%[accent]> [%subject_color:hex%]%subject_name:display% [accent]>[white]",
        )

        data class Hub(
            val enabled: Boolean = false,
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

        companion object {
            private val NAME_REGEX = Regex("^[a-z0-9](-?[a-z0-9])+\$")
        }
    }

    data class Discord(
        val token: Secret,
        val roles: Map<Role, Long> = emptyMap(),
        val categories: Categories,
        val channels: Channels,
        val mindustryVersion: String = "145",
    ) : ServerConfig {
        override val name: String = "discord"

        data class Categories(
            val liveChat: Long,
        )

        data class Channels(
            val notifications: Long,
            val maps: Long,
        )
    }
}

sealed interface StorageConfig {
    data class Minio(
        val host: String = "localhost",
        val port: Int = 9000,
        val secure: Boolean = false,
        val accessKey: Secret = Secret("minioadmin"),
        val secretKey: Secret = Secret("minioadmin"),
        val bucket: String = "imperium",
    ) : StorageConfig
}

sealed interface ImageAnalysisConfig {
    data object None : ImageAnalysisConfig

    data class SightEngine(
        val sightEngineClient: String,
        val sightEngineSecret: Secret,
        val nudityThreshold: Float = 0.5F,
        val goreThreshold: Float = 0.5F,
        val offensiveThreshold: Float = 0.5F,
    ) : ImageAnalysisConfig
}
