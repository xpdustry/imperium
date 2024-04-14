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
package com.xpdustry.imperium.discord

import com.xpdustry.imperium.common.CommonModule
import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.module
import com.xpdustry.imperium.common.inject.single
import com.xpdustry.imperium.common.localization.LocalizationSource
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.discord.command.ButtonCommandRegistry
import com.xpdustry.imperium.discord.command.CloudCommandRegistry
import com.xpdustry.imperium.discord.content.AnukenMindustryContentHandler
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.localization.BundleLocalizationSource
import com.xpdustry.imperium.discord.service.DiscordService
import com.xpdustry.imperium.discord.service.SimpleDiscordService
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.io.path.Path

@Suppress("FunctionName")
fun DiscordModule() =
    module("discord") {
        include(CommonModule())

        single<DiscordService> { SimpleDiscordService(get(), get(), get()) }

        single<Path>("directory") { Path(".") }

        single<AnnotationScanner>("slash") { CloudCommandRegistry(get(), get(), get()) }

        single<AnnotationScanner>("button") { ButtonCommandRegistry(get()) }

        single<MindustryContentHandler> { AnukenMindustryContentHandler(get("directory"), get()) }

        single<ServerConfig.Discord> {
            get<ImperiumConfig>().server as? ServerConfig.Discord
                ?: error("The current server configuration is not Discord")
        }

        single<Supplier<Discovery.Data>>("discovery") { Supplier { Discovery.Data.Discord } }

        single<ImperiumVersion> {
            ImperiumVersion.parse(
                this::class.java.getResourceAsStream("/imperium-version.txt")!!.reader().readText())
        }

        single<LocalizationSource> { BundleLocalizationSource(get()) }
    }
