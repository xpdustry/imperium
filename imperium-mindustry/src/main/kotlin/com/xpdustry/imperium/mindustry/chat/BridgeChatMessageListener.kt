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
import com.xpdustry.imperium.common.bridge.MindustryServerMessage
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.BLURPLE
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toHexString
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.placeholder.PlaceholderContext
import com.xpdustry.imperium.mindustry.placeholder.PlaceholderPipeline
import fr.xpdustry.distributor.api.event.EventHandler
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Iconc

private val logger = logger("ROOT")

class BridgeChatMessageListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Mindustry>()
    private val messenger = instances.get<Messenger>()
    private val chatMessagePipeline = instances.get<ChatMessagePipeline>()
    private val placeholderPipeline = instances.get<PlaceholderPipeline>()

    override fun onImperiumInit() {
        messenger.consumer<BridgeChatMessage> {
            if (it.serverName != config.name) return@consumer
            // The null target represents the server, for logging purposes
            (Entities.PLAYERS + listOf(null)).forEach { target ->
                ImperiumScope.MAIN.launch {
                    val processed =
                        chatMessagePipeline.pump(ChatMessageContext(null, target, it.message))
                    if (processed.isBlank()) return@launch
                    target?.sendMessage(
                        "[${BLURPLE.toHexString()}]${getDiscordChatPrefix()} ${formatChatMessage(it.sender, processed)}")
                    if (target == null) {
                        logger.info(
                            "&fi&lcDiscord ({}): &fr&lw${processed.stripMindustryColors()}",
                            it.sender.name)
                    }
                }
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        ImperiumScope.MAIN.launch {
            messenger.publish(
                MindustryPlayerMessage(
                    config.identity, event.player.identity, MindustryPlayerMessage.Action.Join))
        }

    @EventHandler
    fun onPlayerQuit(event: EventType.PlayerLeave) =
        ImperiumScope.MAIN.launch {
            messenger.publish(
                MindustryPlayerMessage(
                    config.identity, event.player.identity, MindustryPlayerMessage.Action.Quit))
        }

    @EventHandler
    fun onPlayerChat(event: ProcessedPlayerChatEvent) =
        ImperiumScope.MAIN.launch {
            messenger.publish(
                MindustryPlayerMessage(
                    config.identity,
                    event.player.identity,
                    MindustryPlayerMessage.Action.Chat(Strings.stripColors(event.message))),
            )
        }

    @EventHandler
    fun onGameOver(event: EventType.GameOverEvent) {
        val message =
            if (Vars.state.rules.waves) {
                "Game over! Reached wave ${Vars.state.wave} with ${Entities.PLAYERS.size} players online on map ${Vars.state.map.name()}."
            } else {
                "Game over! Team ${event.winner.name} is victorious with ${Entities.PLAYERS.size} players online on map ${Vars.state.map.name()}."
            }
        ImperiumScope.MAIN.launch {
            messenger.publish(MindustryServerMessage(config.identity, message, chat = false))
        }
    }

    private suspend fun formatChatMessage(subject: Identity, message: String): String {
        return placeholderPipeline.pump(PlaceholderContext(subject, config.templates.chatFormat)) +
            " " +
            message
    }

    private fun getDiscordChatPrefix(): String {
        return config.templates.chatPrefix.replace("%prefix%", Iconc.discord.toString())
    }
}
