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
import java.awt.Color
import java.time.Duration
import java.util.Locale

data class ImperiumConfig(
    val network: NetworkConfig = NetworkConfig(),
    val translator: TranslatorConfig = TranslatorConfig.None,
    val database: DatabaseConfig = DatabaseConfig.Mongo(),
    val messenger: MessengerConfig = MessengerConfig.RabbitMQ(),
    val server: ServerConfig = ServerConfig.None,
    val language: Locale = Locale.ENGLISH,
    val storage: StorageConfig = StorageConfig.Minio(),
    val security: SecurityConfig = SecurityConfig(),
)

data class NetworkConfig(
    val vpnDetection: VpnDetectionConfig = VpnDetectionConfig.None,
    val discoveryInterval: Duration = Duration.ofSeconds(5L),
) {
    sealed interface VpnDetectionConfig {
        data object None : VpnDetectionConfig
        data class IPHub(val token: Secret) : VpnDetectionConfig
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

    data object None : ServerConfig {
        override val name: String = "none"
    }

    data class Mindustry(
        override val name: String,
        val quotes: List<String> = listOf("Bonjour", "The best mindustry server of all time"),
        val hub: Boolean = false,
        val history: History = History(),
        val color: Color = Color.WHITE,
    ) : ServerConfig {
        init {
            require(name != "discord") { "Mindustry Server name cannot be discord" }
        }
        data class History(
            val tileEntriesLimit: Int = 10,
            val playerEntriesLimit: Int = 200,
        )
    }

    data class Discord(
        val token: Secret,
        val roles: Roles,
        val categories: Categories,
        val channels: Channels,
        val mindustryVersion: String = "145",
    ) : ServerConfig {
        override val name: String = "discord"

        data class Roles(
            val administrator: Long,
            val moderator: Long,
            val verified: Long,
        )

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

data class SecurityConfig(
    val imageAnalysis: ImageAnalysis = ImageAnalysis.None,
) {
    sealed interface ImageAnalysis {
        data object None : ImageAnalysis
        data class Google(
            val threshold: Float = 0.8F,
            val adultWeight: Float = 1F,
            val racyWeight: Float = 1F,
            val violenceWeight: Float = 1F,
        ) : ImageAnalysis
    }
}
