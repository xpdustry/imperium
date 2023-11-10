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
package com.xpdustry.imperium.mindustry.misc

import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.version.MindustryVersion
import java.net.InetAddress
import mindustry.Vars
import mindustry.core.GameState
import mindustry.core.Version
import mindustry.game.Gamemode
import mindustry.net.Administration

fun getMindustryServerInfo(): Discovery.Data.Mindustry =
    Discovery.Data.Mindustry(
        Administration.Config.serverName.string(),
        // Our servers run within a pterodactyl container, so we can use the SERVER_IP environment
        // variable
        System.getenv("SERVER_IP")?.toInetAddress() ?: InetAddress.getLocalHost(),
        Administration.Config.port.num(),
        Vars.state.map.name(),
        Administration.Config.desc.string(),
        Vars.state.wave,
        Entities.PLAYERS.size,
        Vars.netServer.admins.playerLimit,
        getMindustryVersion(),
        getGameMode(),
        Vars.state.rules.modeName,
        when (Vars.state.state!!) {
            GameState.State.playing -> Discovery.Data.Mindustry.State.PLAYING
            GameState.State.paused -> Discovery.Data.Mindustry.State.PAUSED
            GameState.State.menu -> Discovery.Data.Mindustry.State.STOPPED
        })

fun getGameMode(): Discovery.Data.Mindustry.GameMode =
    when (Vars.state.rules.mode()!!) {
        Gamemode.attack -> Discovery.Data.Mindustry.GameMode.ATTACK
        Gamemode.pvp -> Discovery.Data.Mindustry.GameMode.PVP
        Gamemode.sandbox -> Discovery.Data.Mindustry.GameMode.SANDBOX
        Gamemode.survival -> Discovery.Data.Mindustry.GameMode.SURVIVAL
        Gamemode.editor -> Discovery.Data.Mindustry.GameMode.EDITOR
    }

fun getMindustryVersion(): MindustryVersion =
    MindustryVersion(
        Version.number,
        Version.build.coerceAtLeast(0),
        Version.revision,
        getVersionType(),
    )

// Yes, this is a mess
private fun getVersionType(): MindustryVersion.Type =
    if (Version.build == -1) {
        MindustryVersion.Type.CUSTOM
    } else
        when (Version.modifier.lowercase()) {
            "alpha" -> MindustryVersion.Type.ALPHA
            else ->
                when (Version.type) {
                    "official" -> MindustryVersion.Type.OFFICIAL
                    "bleeding-edge" -> MindustryVersion.Type.BLEEDING_EDGE
                    else -> MindustryVersion.Type.CUSTOM
                }
        }
