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
package com.xpdustry.imperium.mindustry.chat

import arc.util.Strings
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.audience.Audience
import com.xpdustry.flex.FlexAPI
import com.xpdustry.flex.message.FlexPlayerChatEvent
import com.xpdustry.flex.message.MessageContext
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.bridge.BridgeChatMessage
import com.xpdustry.imperium.common.bridge.MindustryPlayerMessage
import com.xpdustry.imperium.common.bridge.MindustryServerMessage
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.mindustry.bridge.DiscordAudience
import com.xpdustry.imperium.mindustry.misc.Entities
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType

class BridgeChatMessageListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val messenger = instances.get<Messenger>()
    private val accounts = instances.get<AccountManager>()

    override fun onImperiumInit() {
        messenger.consumer<BridgeChatMessage> {
            if (it.serverName != config.server.name) return@consumer

            val forServer =
                FlexAPI.get()
                    .messages
                    .pump(
                        MessageContext(
                            Audience.empty(),
                            Distributor.get().audienceProvider.server,
                            it.message,
                            filter = true,
                        )
                    )
                    .await()

            if (forServer.isNotBlank()) {
                ROOT_LOGGER.info("&fi&lcDiscord ({}&fi&lc): &fr&lw${forServer.stripMindustryColors()}", it.senderName)
            }

            val account = accounts.selectByDiscord(it.discord)
            FlexAPI.get()
                .messages
                .broadcast(
                    DiscordAudience(
                        it.senderName,
                        account?.rank ?: Rank.EVERYONE,
                        account?.playtime?.inWholeHours?.toInt(),
                        config.language,
                    ),
                    Distributor.get().audienceProvider.players,
                    it.message,
                )
                .await()
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        ImperiumScope.MAIN.launch {
            messenger.publish(
                MindustryPlayerMessage(
                    config.server.name,
                    event.player.info.plainLastName(),
                    MindustryPlayerMessage.Action.Join,
                )
            )
        }

    @EventHandler
    fun onPlayerQuit(event: EventType.PlayerLeave) =
        ImperiumScope.MAIN.launch {
            messenger.publish(
                MindustryPlayerMessage(
                    config.server.name,
                    event.player.info.plainLastName(),
                    MindustryPlayerMessage.Action.Quit,
                )
            )
        }

    @EventHandler
    fun onPlayerChat(event: FlexPlayerChatEvent) =
        ImperiumScope.MAIN.launch {
            messenger.publish(
                MindustryPlayerMessage(
                    config.server.name,
                    event.player.player.info.plainLastName(),
                    MindustryPlayerMessage.Action.Chat(Strings.stripColors(event.message)),
                )
            )
        }

    @EventHandler
    fun onGameOver(event: EventType.GameOverEvent) {
        val message =
            if (Vars.state.rules.waves) {
                "Game over! Reached wave ${Vars.state.wave} with ${Entities.getPlayers().size} players online on map ${Vars.state.map.name().stripMindustryColors()}."
            } else {
                "Game over! Team ${event.winner.name} is victorious with ${Entities.getPlayers().size} players online on map ${Vars.state.map.name().stripMindustryColors()}."
            }
        ImperiumScope.MAIN.launch {
            messenger.publish(MindustryServerMessage(config.server.name, message, chat = false))
        }
    }

    @EventHandler
    fun onNextMap(event: EventType.PlayEvent) {
        val message = "New game started on **\"${Vars.state.map.name().stripMindustryColors()}\"**."
        ImperiumScope.MAIN.launch {
            messenger.publish(MindustryServerMessage(config.server.name, message, chat = false))
        }
    }

    companion object {
        private val ROOT_LOGGER = logger("ROOT")
    }
}
