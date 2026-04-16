// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.audience.Audience
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.MindustryServerMessage
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.asAudience
import mindustry.Vars
import mindustry.gen.Player
import org.incendo.cloud.annotation.specifier.Greedy

@Inject
class ChatCommand(
    private val messenger: MessageService,
    private val config: ImperiumConfig,
    private val messages: MindustryMessagePipeline,
) : ImperiumApplication.Listener {

    @ImperiumCommand(["t"])
    @ClientSide
    suspend fun onTeamChatCommand(sender: CommandSender, @Greedy message: String) {
        if (
            messages.pump(MindustryMessageContext(sender.audience, sender.audience, message, filter = true)).isBlank()
        ) {
            return
        }
        messages.broadcast(
            sender.player.asAudience,
            Distributor.get().audienceProvider.getTeam(sender.player.team()),
            message,
            MindustryMessageTemplate.TEAM,
        )
    }

    @ImperiumCommand(["w"])
    @ClientSide
    suspend fun onWhisperCommand(sender: CommandSender, target: Player, @Greedy message: String) {
        if (sender.player == target) {
            sender.error("You can't whisper to yourself.")
            return
        }
        if (
            messages.pump(MindustryMessageContext(sender.audience, sender.audience, message, filter = true)).isBlank()
        ) {
            return
        }
        messages.broadcast(
            sender.player.asAudience,
            Audience.of(sender.player.asAudience, target.asAudience),
            message,
            MindustryMessageTemplate.WHISPER,
        )
    }

    @ImperiumCommand(["say"])
    @ServerSide
    suspend fun onServerMessageCommand(sender: CommandSender, @Greedy message: String) {
        if (!Vars.state.isGame) {
            sender.error("Not hosting. Host a game first.")
            return
        }

        val forServer = messages.pump(MindustryMessageContext(sender.audience, sender.audience, message))
        if (forServer.isBlank()) {
            return
        }

        sender.reply("&fi&lcServer: &fr&lw$forServer")
        messenger.broadcast(MindustryServerMessage(config.server.name, forServer, chat = true))

        messages.broadcast(
            Distributor.get().audienceProvider.server,
            Distributor.get().audienceProvider.players,
            message,
            MindustryMessageTemplate.SAY,
        )
    }
}
