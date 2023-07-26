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
package com.xpdustry.foundation.mindustry

import com.xpdustry.foundation.common.commonModule
import com.xpdustry.foundation.common.config.FoundationConfig
import com.xpdustry.foundation.common.inject.factory
import com.xpdustry.foundation.common.inject.get
import com.xpdustry.foundation.common.inject.module
import com.xpdustry.foundation.common.inject.single
import com.xpdustry.foundation.common.network.MindustryServerInfo
import com.xpdustry.foundation.common.version.MindustryVersion
import com.xpdustry.foundation.mindustry.chat.ChatMessagePipeline
import com.xpdustry.foundation.mindustry.chat.SimpleChatMessagePipeline
import com.xpdustry.foundation.mindustry.command.FoundationPluginCommandManager
import com.xpdustry.foundation.mindustry.history.BlockHistory
import com.xpdustry.foundation.mindustry.history.SimpleBlockHistory
import com.xpdustry.foundation.mindustry.placeholder.PlaceholderPipeline
import com.xpdustry.foundation.mindustry.placeholder.SimplePlaceholderManager
import com.xpdustry.foundation.mindustry.verification.SimpleVerificationPipeline
import com.xpdustry.foundation.mindustry.verification.VerificationPipeline
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import mindustry.Vars
import mindustry.core.Version
import mindustry.game.Gamemode
import mindustry.gen.Groups
import mindustry.net.Administration
import java.nio.file.Path
import java.util.function.Supplier

fun mindustryModule(plugin: FoundationPlugin) = module("mindustry") {
    include(commonModule())

    factory<Supplier<MindustryServerInfo?>> {
        val config = get<FoundationConfig>()
        Supplier { getMindustryServerInfo(config) }
    }

    single<BlockHistory> {
        SimpleBlockHistory(get())
    }

    single<MindustryPlugin> {
        plugin
    }

    single<VerificationPipeline> {
        SimpleVerificationPipeline()
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

    single<FoundationPluginCommandManager>("client") {
        plugin.clientCommandManager
    }

    single<FoundationPluginCommandManager>("server") {
        plugin.serverCommandManager
    }
}

private fun getMindustryServerInfo(config: FoundationConfig): MindustryServerInfo? {
    if (Vars.state.isMenu) {
        return null
    }
    return MindustryServerInfo(
        config.mindustry.serverName,
        config.mindustry.host,
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
