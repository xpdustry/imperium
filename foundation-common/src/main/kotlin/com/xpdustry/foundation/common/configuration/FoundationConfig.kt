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
package com.xpdustry.foundation.common.configuration

import com.sksamuel.hoplite.Masked

open class FoundationConfig(
    val network: NetworkConfig = NetworkConfig(),
    val translator: TranslatorConfig = TranslatorConfig(),
    val mongo: MongoConfig = MongoConfig(),
    val mindustry: MindustryConfig = MindustryConfig(),
    val discord: DiscordConfig = DiscordConfig(),
)

data class NetworkConfig(
    val ipHub: Masked? = null,
)

data class TranslatorConfig(
    val deepl: Masked? = null,
)

data class MongoConfig(
    val host: String = "localhost",
    val port: Int = 27017,
    val username: String = "",
    val password: Masked = Masked(""),
    val ssl: Boolean = false,
    val database: String = "foundation",
    val authDatabase: String = "admin",
)

data class MindustryConfig(
    val serverName: String = "unknown",
    val quotes: List<String> = listOf("Bonjour"),
    val hub: Boolean = false,
    val history: HistoryConfig = HistoryConfig(),
)

data class HistoryConfig(
    val tileEntriesLimit: Int = 10,
    val playerEntriesLimit: Int = 200,
)

data class DiscordConfig(
    val token: Masked? = null,
)
