// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.bridge

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.bridge.RequestingPlayerTracker
import com.xpdustry.imperium.common.collection.LimitedList
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.identity
import kotlinx.coroutines.launch
import mindustry.game.EventType

class MindustryPlayerTracker(
    messenger: MessageService,
    private val config: ImperiumConfig,
    private val users: UserManager,
) : RequestingPlayerTracker(messenger), ImperiumApplication.Listener {

    private val joins = LimitedList<PlayerTracker.Entry>(30)
    private val online = mutableMapOf<Int, PlayerTracker.Entry>()

    override fun onImperiumInit() {
        messenger.subscribe<PlayerListRequest> {
            if (it.server != config.server.name) return@subscribe
            val entries =
                when (it.type) {
                    PlayerListRequest.Type.JOIN -> joins
                    PlayerListRequest.Type.ONLINE -> online.values.toList()
                }
            messenger.broadcast(PlayerListResponse(entries, it.id))
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
