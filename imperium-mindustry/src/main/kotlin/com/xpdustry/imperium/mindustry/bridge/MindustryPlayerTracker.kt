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
package com.xpdustry.imperium.mindustry.bridge

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.bridge.RequestingPlayerTracker
import com.xpdustry.imperium.common.collection.LimitedList
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.database.snowflake.SnowflakeGenerator
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.function
import com.xpdustry.imperium.mindustry.misc.identity
import fr.xpdustry.distributor.api.event.EventHandler
import kotlinx.coroutines.launch
import mindustry.game.EventType

class MindustryPlayerTracker(
    messenger: Messenger,
    private val config: ServerConfig.Mindustry,
    private val snowflake: SnowflakeGenerator
) : RequestingPlayerTracker(messenger), ImperiumApplication.Listener {

    private val joins = LimitedList<PlayerTracker.Entry>(30)
    private val quits = LimitedList<PlayerTracker.Entry>(30)
    private val online = mutableMapOf<Int, PlayerTracker.Entry>()

    override fun onImperiumInit() {
        messenger.function<PlayerListRequest, PlayerListResponse> {
            if (it.server != config.name) {
                return@function null
            }
            PlayerListResponse(
                when (it.type) {
                    PlayerListRequest.Type.JOIN -> joins
                    PlayerListRequest.Type.QUIT -> quits
                    PlayerListRequest.Type.ONLINE -> online.values.toList()
                })
        }

        messenger.function<PlayerLookupRequest, PlayerLookupResponse> {
            for (list in listOf(joins, quits)) {
                for (entry in list) {
                    if (entry.tid == it.tid) {
                        return@function PlayerLookupResponse(entry)
                    }
                }
            }
            null
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        ImperiumScope.MAIN.launch {
            val entry = PlayerTracker.Entry(event.player.identity, snowflake.generate())
            joins.add(entry)
            online[event.player.id] = entry
        }

    @EventHandler
    fun onPlayerQuit(event: EventType.PlayerLeave) =
        ImperiumScope.MAIN.launch {
            val entry = online.remove(event.player.id)!!
            quits.add(entry)
        }
}
