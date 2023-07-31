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
import com.xpdustry.imperium.common.application.ImperiumPlatform
import com.xpdustry.imperium.common.bridge.BridgeMessage
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.filterAndCast
import com.xpdustry.imperium.common.misc.onErrorResumeEmpty
import com.xpdustry.imperium.mindustry.misc.MindustryScheduler
import fr.xpdustry.distributor.api.event.EventHandler
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Iconc

class BridgeChatMessageListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val messenger = instances.get<Messenger>()

    override fun onImperiumInit() {
        messenger.on(BridgeMessage::class)
            .filter { it.origin == ImperiumPlatform.DISCORD && it.serverName == config.mindustry.serverName }
            .map(BridgeMessage::payload)
            .filterAndCast(BridgeMessage.Payload.PlayerChat::class)
            .publishOn(MindustryScheduler)
            .subscribe {
                Call.sendMessage("[coral][[[white]${Iconc.discord}[]][[[orange]${it.playerName}[coral]]:[white] ${it.message}")
            }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        sendBridgeMessage(BridgeMessage.Payload.PlayerJoin(event.player.plainName()))

    @EventHandler
    fun onPlayerQuit(event: EventType.PlayerLeave) =
        sendBridgeMessage(BridgeMessage.Payload.PlayerQuit(event.player.plainName()))

    @EventHandler
    fun onPlayerChat(event: ProcessedPlayerChatEvent) =
        sendBridgeMessage(
            BridgeMessage.Payload.PlayerChat(event.player.plainName(), Strings.stripColors(event.message)),
        )

    @EventHandler
    fun onGameOver(event: EventType.GameOverEvent) =
        sendBridgeMessage(BridgeMessage.Payload.System("Game over! ${event.winner.name} won!"))

    private fun sendBridgeMessage(payload: BridgeMessage.Payload) =
        messenger.publish(BridgeMessage(config.mindustry.serverName, ImperiumPlatform.MINDUSTRY, payload))
            .onErrorResumeEmpty { logger.error("Failed to send bridge message", it) }
            .subscribe()

    companion object {
        private val logger by LoggerDelegate()
    }
}
