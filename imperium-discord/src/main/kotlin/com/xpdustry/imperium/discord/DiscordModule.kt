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

import com.xpdustry.imperium.common.commonModule
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.module
import com.xpdustry.imperium.common.inject.single
import com.xpdustry.imperium.common.network.MindustryServerInfo
import com.xpdustry.imperium.discord.command.CommandManager
import com.xpdustry.imperium.discord.command.SimpleCommandManager
import com.xpdustry.imperium.discord.content.AnukenMindustryContentHandler
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.service.DiscordService
import com.xpdustry.imperium.discord.service.SimpleDiscordService
import reactor.core.scheduler.Schedulers
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.function.Supplier
import kotlin.io.path.Path

fun discordModule() = module("discord") {
    include(commonModule())

    single<DiscordService> {
        SimpleDiscordService(get())
    }

    single<Path>("directory") {
        Path(".")
    }

    single<CommandManager> {
        SimpleCommandManager(get())
    }

    single<Supplier<MindustryServerInfo?>> {
        Supplier { null }
    }

    single<MindustryContentHandler> {
        AnukenMindustryContentHandler(get("directory"), get())
    }

    single<Executor>("scheduler") {
        Executor { runnable -> Schedulers.boundedElastic().schedule(runnable) }
    }

    single<ServerConfig.Discord> {
        get<ImperiumConfig>().server as? ServerConfig.Discord
            ?: error("The current server configuration is not Discord")
    }
}
