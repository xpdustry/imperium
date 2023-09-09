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
package com.xpdustry.imperium.mindustry.chat

import arc.util.Strings
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.bridge.BridgeChatMessage
import com.xpdustry.imperium.common.bridge.MindustryPlayerMessage
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.mindustry.misc.identity
import fr.xpdustry.distributor.api.event.EventHandler
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Iconc

class BridgeChatMessageListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val messenger = instances.get<Messenger>()

    override fun onImperiumInit() {
        messenger.subscribe<BridgeChatMessage> {
            if (it.serverName != config.server.name) return@subscribe
            Call.sendMessage(
                "[coral][[[white]${Iconc.discord}[]][[[orange]${it.senderName}[coral]]:[white] ${it.message}",
            )
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) = ImperiumScope.MAIN.launch {
        messenger.publish(MindustryPlayerMessage.Join(config.server.name, event.player.identity))
    }

    @EventHandler
    fun onPlayerQuit(event: EventType.PlayerLeave) = ImperiumScope.MAIN.launch {
        messenger.publish(MindustryPlayerMessage.Quit(config.server.name, event.player.identity))
    }

    @EventHandler
    fun onPlayerChat(event: ProcessedPlayerChatEvent) = ImperiumScope.MAIN.launch {
        messenger.publish(
            MindustryPlayerMessage.Chat(config.server.name, event.player.identity, Strings.stripColors(event.message)),
        )
    }
}
