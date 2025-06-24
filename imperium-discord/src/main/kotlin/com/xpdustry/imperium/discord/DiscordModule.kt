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
package com.xpdustry.imperium.discord

import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.inject.MutableInstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.provider
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.discord.command.MenuCommandRegistry
import com.xpdustry.imperium.discord.command.ModalCommandRegistry
import com.xpdustry.imperium.discord.command.SlashCommandRegistry
import com.xpdustry.imperium.discord.content.AnukenMindustryContentHandler
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.service.DiscordService
import com.xpdustry.imperium.discord.service.SimpleDiscordService
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.io.path.Path

fun MutableInstanceManager.registerDiscordModule() {
    provider<DiscordService> { SimpleDiscordService(get(), get(), get()) }

    provider<Path>("directory") { Path(".") }

    provider<AnnotationScanner>("slash") { SlashCommandRegistry(get(), get()) }

    provider<AnnotationScanner>("menu") { MenuCommandRegistry(get()) }

    provider<AnnotationScanner>("modal") { ModalCommandRegistry(get()) }

    provider<MindustryContentHandler> { AnukenMindustryContentHandler(get("directory"), get()) }

    provider<Supplier<Discovery.Data>>("discovery") { Supplier { Discovery.Data.Discord } }

    provider<ImperiumVersion> {
        ImperiumVersion.parse(this::class.java.getResourceAsStream("/imperium-version.txt")!!.reader().readText())
    }
}
