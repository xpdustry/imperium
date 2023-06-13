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
package com.xpdustry.foundation.mindustry.core

import com.google.inject.Inject
import com.google.inject.Provider
import com.xpdustry.foundation.common.application.FoundationMetadata
import com.xpdustry.foundation.common.config.FoundationConfig
import com.xpdustry.foundation.common.network.MindustryServerInfo
import com.xpdustry.foundation.common.network.ServerInfo
import com.xpdustry.foundation.common.version.MindustryVersion
import mindustry.Vars
import mindustry.core.Version
import mindustry.game.Gamemode
import mindustry.gen.Groups
import mindustry.net.Administration

class MindustryServerInfoProvider @Inject constructor(
    private val metadata: FoundationMetadata,
    private val config: FoundationConfig,
) : Provider<ServerInfo> {

    override fun get(): ServerInfo = ServerInfo(
        metadata,
        getMindustryServerInfo(),
    )

    private fun getMindustryServerInfo(): MindustryServerInfo? {
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
}
