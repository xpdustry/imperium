// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.chat

import arc.util.Strings
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.audience.Audience
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.bridge.BridgeChatMessage
import com.xpdustry.imperium.common.bridge.MindustryPlayerMessage
import com.xpdustry.imperium.common.bridge.MindustryServerMessage
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.mindustry.bridge.DiscordAudience
import com.xpdustry.imperium.mindustry.misc.Entities
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType

@Inject
class BridgeChatMessageListener(
    private val config: ImperiumConfig,
    private val messenger: MessageService,
    private val accounts: AccountManager,
    private val messages: MindustryMessagePipeline,
) : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        messenger.subscribe<BridgeChatMessage> {
            if (it.serverName != config.server.name) return@subscribe

            val forServer =
                messages.pump(
                    MindustryMessageContext(
                        Audience.empty(),
                        Distributor.get().audienceProvider.server,
                        it.message,
                        filter = true,
                    )
                )

            if (forServer.isNotBlank()) {
                ROOT_LOGGER.info("&fi&lcDiscord ({}&fi&lc): &fr&lw${forServer.stripMindustryColors()}", it.senderName)
            }

            val account = accounts.selectByDiscord(it.discord)
            messages.broadcast(
                DiscordAudience(
                    it.senderName,
                    account?.rank ?: Rank.EVERYONE,
                    account?.playtime?.inWholeHours?.toInt(),
                    config.language,
                ),
                Distributor.get().audienceProvider.players,
                it.message,
            )
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        ImperiumScope.MAIN.launch {
            messenger.broadcast(
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
            messenger.broadcast(
                MindustryPlayerMessage(
                    config.server.name,
                    event.player.info.plainLastName(),
                    MindustryPlayerMessage.Action.Quit,
                )
            )
        }

    @EventHandler
    fun onPlayerChat(event: MindustryPlayerChatEvent) =
        ImperiumScope.MAIN.launch {
            messenger.broadcast(
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
            messenger.broadcast(MindustryServerMessage(config.server.name, message, chat = false))
        }
    }

    @EventHandler
    fun onNextMap(event: EventType.PlayEvent) {
        val message = "New game started on **\"${Vars.state.map.name().stripMindustryColors()}\"**."
        ImperiumScope.MAIN.launch {
            messenger.broadcast(MindustryServerMessage(config.server.name, message, chat = false))
        }
    }

    companion object {
        private val ROOT_LOGGER = logger("ROOT")
    }
}
