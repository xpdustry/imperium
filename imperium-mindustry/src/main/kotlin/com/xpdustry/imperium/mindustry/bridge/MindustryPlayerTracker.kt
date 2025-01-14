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
package com.xpdustry.imperium.mindustry.bridge

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.bridge.RequestingPlayerTracker
import com.xpdustry.imperium.common.collection.LimitedList
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.function
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.identity
import kotlinx.coroutines.launch
import mindustry.game.EventType

class MindustryPlayerTracker(messenger: Messenger, private val config: ImperiumConfig, private val users: UserManager) :
    RequestingPlayerTracker(messenger), ImperiumApplication.Listener {

    private val joins = LimitedList<PlayerTracker.Entry>(30)
    private val online = mutableMapOf<Int, PlayerTracker.Entry>()

    override fun onImperiumInit() {
        messenger.function<PlayerListRequest, PlayerListResponse> {
            if (it.server != config.server.name) return@function null
            PlayerListResponse(
                when (it.type) {
                    PlayerListRequest.Type.JOIN -> joins
                    PlayerListRequest.Type.ONLINE -> online.values.toList()
                }
            )
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        ImperiumScope.MAIN.launch {
            val id = users.getByIdentity(event.player.identity).id
            val entry = PlayerTracker.Entry(event.player.identity, id)
            joins.add(entry)
            online[id] = entry
        }

    @EventHandler
    fun onPlayerQuit(event: EventType.PlayerLeave) =
        ImperiumScope.MAIN.launch { online.remove(users.getByIdentity(event.player.identity).id)!! }
}
