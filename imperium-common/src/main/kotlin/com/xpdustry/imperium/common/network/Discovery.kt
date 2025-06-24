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
package com.xpdustry.imperium.common.network

import com.xpdustry.imperium.common.serialization.SerializableInetAddress
import com.xpdustry.imperium.common.version.MindustryVersion
import kotlinx.serialization.Serializable

interface Discovery {
    val servers: Map<String, Server>

    fun heartbeat()

    @Serializable data class Server(val name: String, val data: Data)

    @Serializable
    sealed interface Data {
        @Serializable data object Unknown : Data

        @Serializable data object Discord : Data

        @Serializable
        data class Mindustry(
            val name: String,
            val host: SerializableInetAddress,
            val port: Int,
            val mapName: String,
            val description: String,
            val wave: Int,
            val playerCount: Int,
            val playerLimit: Int,
            val gameVersion: MindustryVersion,
            val gamemode: Gamemode,
            val gamemodeName: String?,
            val state: State,
        ) : Data {

            enum class State {
                PLAYING,
                PAUSED,
                STOPPED,
            }

            enum class Gamemode {
                SURVIVAL,
                SANDBOX,
                ATTACK,
                PVP,
                EDITOR,
            }
        }
    }
}
