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

import com.xpdustry.imperium.backend.lifecycle.BackendExitService
import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.bridge.RequestingPlayerTracker
import com.xpdustry.imperium.common.factory.ObjectBinder
import com.xpdustry.imperium.common.factory.ObjectModule
import com.xpdustry.imperium.common.lifecycle.ExitService
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.network.DiscoveryDataSupplier
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.discord.command.MenuCommandRegistry
import com.xpdustry.imperium.discord.command.ModalCommandRegistry
import com.xpdustry.imperium.discord.command.SlashCommandRegistry
import com.xpdustry.imperium.discord.content.AnukenMindustryContentHandler
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.service.DiscordService
import com.xpdustry.imperium.discord.service.SimpleDiscordService
import java.nio.file.Path
import kotlin.io.path.Path

class DiscordModule : ObjectModule {
    override fun configure(binder: ObjectBinder) {
        binder.bind(ExitService::class.java).toImpl(BackendExitService::class.java)
        binder.bind(PlayerTracker::class.java).toImpl(RequestingPlayerTracker::class.java)
        binder.bind(DiscordService::class.java).toImpl(SimpleDiscordService::class.java)
        binder.bind(Path::class.java).named("directory").toInst(Path("."))
        binder.bind(AnnotationScanner::class.java).named("slash").toImpl(SlashCommandRegistry::class.java)
        binder.bind(AnnotationScanner::class.java).named("menu").toImpl(MenuCommandRegistry::class.java)
        binder.bind(AnnotationScanner::class.java).named("modal").toImpl(ModalCommandRegistry::class.java)
        binder.bind(MindustryContentHandler::class.java).toImpl(AnukenMindustryContentHandler::class.java)
        binder.bind(DiscoveryDataSupplier::class.java).toInst(DiscoveryDataSupplier { Discovery.Data.Discord })
        binder
            .bind(ImperiumVersion::class.java)
            .toInst(
                ImperiumVersion.parse(
                    this::class.java.getResourceAsStream("/imperium-version.txt")!!.reader().readText()
                )
            )
    }
}
