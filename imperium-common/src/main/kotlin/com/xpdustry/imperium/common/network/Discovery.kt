// SPDX-License-Identifier: GPL-3.0-only
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
