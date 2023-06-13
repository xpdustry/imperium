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
package com.xpdustry.foundation.common.config

import java.awt.Color
import java.net.InetAddress

data class FoundationConfig(
    val network: NetworkConfig = NetworkConfig(),
    val translator: TranslatorConfig = TranslatorConfig(),
    val mongo: MongoConfig = MongoConfig(),
    val messenger: MessengerConfig = MessengerConfig(),
    val mindustry: MindustryConfig = MindustryConfig(),
    val discord: DiscordConfig = DiscordConfig(),
)

data class NetworkConfig(
    val ipHub: HiddenString? = null,
)

data class TranslatorConfig(
    val deepl: HiddenString? = null,
)

data class MongoConfig(
    val host: String = "localhost",
    val port: Int = 27017,
    val username: String = "",
    val password: HiddenString = HiddenString(""),
    val ssl: Boolean = false,
    val database: String = "foundation",
    val authDatabase: String = "admin",
)

data class MessengerConfig(
    val host: String = "localhost",
    val port: Int = 5672,
    val username: String = "guest",
    val password: HiddenString = HiddenString("guest"),
    val ssl: Boolean = false,
)

data class MindustryConfig(
    val serverName: String = "unknown",
    val quotes: List<String> = listOf("Bonjour", "The best mindustry server of all time"),
    val hub: Boolean = false,
    val history: HistoryConfig = HistoryConfig(),
    val color: Color = Color.WHITE,
    val host: InetAddress = InetAddress.getLocalHost(),
)

data class HistoryConfig(
    val tileEntriesLimit: Int = 10,
    val playerEntriesLimit: Int = 200,
)

data class DiscordConfig(
    val token: HiddenString? = null,
)
