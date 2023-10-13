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
package com.xpdustry.imperium.mindustry

import com.xpdustry.imperium.common.command.CommandRegistry
import com.xpdustry.imperium.common.commonModule
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.factory
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.module
import com.xpdustry.imperium.common.inject.single
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.network.MindustryServerInfo
import com.xpdustry.imperium.common.version.MindustryVersion
import com.xpdustry.imperium.mindustry.chat.ChatMessagePipeline
import com.xpdustry.imperium.mindustry.chat.SimpleChatMessagePipeline
import com.xpdustry.imperium.mindustry.command.MindustryCommandRegistry
import com.xpdustry.imperium.mindustry.history.BlockHistory
import com.xpdustry.imperium.mindustry.history.SimpleBlockHistory
import com.xpdustry.imperium.mindustry.placeholder.PlaceholderPipeline
import com.xpdustry.imperium.mindustry.placeholder.SimplePlaceholderManager
import com.xpdustry.imperium.mindustry.security.GatekeeperPipeline
import com.xpdustry.imperium.mindustry.security.SimpleGatekeeperPipeline
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import mindustry.Vars
import mindustry.core.Version
import mindustry.game.Gamemode
import mindustry.gen.Groups
import mindustry.net.Administration
import java.net.InetAddress
import java.nio.file.Path
import java.util.function.Supplier

fun mindustryModule(plugin: ImperiumPlugin) = module("mindustry") {
    include(commonModule())

    factory<Supplier<MindustryServerInfo?>> {
        val config = get<ImperiumConfig>()
        Supplier { getMindustryServerInfo(config) }
    }

    single<BlockHistory> {
        SimpleBlockHistory(get())
    }

    single<MindustryPlugin> {
        plugin
    }

    single<GatekeeperPipeline> {
        SimpleGatekeeperPipeline()
    }

    single<ChatMessagePipeline> {
        SimpleChatMessagePipeline()
    }

    single<PlaceholderPipeline> {
        SimplePlaceholderManager()
    }

    single<Path>("directory") {
        plugin.directory
    }

    single<ServerConfig.Mindustry> {
        get<ImperiumConfig>().server as? ServerConfig.Mindustry
            ?: error("The current server configuration is not Mindustry")
    }

    single<CommandRegistry> {
        MindustryCommandRegistry(get(), get(), get())
    }
}

private fun getMindustryServerInfo(config: ImperiumConfig): MindustryServerInfo? {
    if (Vars.state.isMenu) {
        return null
    }
    return MindustryServerInfo(
        config.server.name,
        // Our servers run within a pterodactyl container, so we can use the SERVER_IP environment variable
        System.getenv("SERVER_IP")?.toInetAddress() ?: InetAddress.getLocalHost(),
        Administration.Config.port.num(),
        Vars.state.map.name(),
        Administration.Config.desc.string(),
        Vars.state.wave,
        Groups.player.size(),
        Vars.netServer.admins.playerLimit,
        getVersion(),
        getGameMode(),
        Vars.state.rules.modeName,
    )
}

private fun getGameMode(): MindustryServerInfo.GameMode = when (Vars.state.rules.mode()!!) {
    Gamemode.attack -> MindustryServerInfo.GameMode.ATTACK
    Gamemode.pvp -> MindustryServerInfo.GameMode.PVP
    Gamemode.sandbox -> MindustryServerInfo.GameMode.SANDBOX
    Gamemode.survival -> MindustryServerInfo.GameMode.SURVIVAL
    Gamemode.editor -> MindustryServerInfo.GameMode.EDITOR
}

private fun getVersion(): MindustryVersion = MindustryVersion(
    Version.number,
    Version.build.coerceAtLeast(0),
    Version.revision,
    getVersionType(),
)

// Yes, this is a mess
private fun getVersionType(): MindustryVersion.Type {
    if (Version.build == -1) {
        return MindustryVersion.Type.CUSTOM
    }
    return when (Version.modifier.lowercase()) {
        "alpha" -> MindustryVersion.Type.ALPHA
        "release" -> when (Version.type) {
            "official" -> MindustryVersion.Type.OFFICIAL
            "bleeding-edge" -> MindustryVersion.Type.BLEEDING_EDGE
            else -> MindustryVersion.Type.CUSTOM
        }
        else -> {
            MindustryVersion.Type.CUSTOM
            when (Version.type) {
                "official" -> MindustryVersion.Type.OFFICIAL
                "bleeding-edge" -> MindustryVersion.Type.BLEEDING_EDGE
                else -> MindustryVersion.Type.CUSTOM
            }
        }
    }
}
