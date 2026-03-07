// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.audience.Audience
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.flex.FlexAPI
import com.xpdustry.flex.message.MessageContext
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.MindustryServerMessage
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.asAudience
import kotlinx.coroutines.future.await
import mindustry.Vars
import mindustry.gen.Player
import org.incendo.cloud.annotation.specifier.Greedy

class ChatCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val messenger = instances.get<MessageService>()
    private val config = instances.get<ImperiumConfig>()

    @ImperiumCommand(["t"])
    @ClientSide
    suspend fun onTeamChatCommand(sender: CommandSender, @Greedy message: String) {
        if (
            FlexAPI.get()
                .messages
                .pump(MessageContext(sender.audience, sender.audience, message, filter = true))
                .await()
                .isBlank()
        ) {
            return
        }
        FlexAPI.get()
            .messages
            .broadcast(
                sender.player.asAudience,
                Distributor.get().audienceProvider.getTeam(sender.player.team()),
                message,
                "mindustry_chat_team",
            )
            .await()
    }

    @ImperiumCommand(["w"])
    @ClientSide
    suspend fun onWhisperCommand(sender: CommandSender, target: Player, @Greedy message: String) {
        if (sender.player == target) {
            sender.error("You can't whisper to yourself.")
            return
        }
        if (
            FlexAPI.get()
                .messages
                .pump(MessageContext(sender.audience, sender.audience, message, filter = true))
                .await()
                .isBlank()
        ) {
            return
        }
        FlexAPI.get()
            .messages
            .broadcast(
                sender.player.asAudience,
                Audience.of(sender.player.asAudience, target.asAudience),
                message,
                "mindustry_chat_whisper",
            )
            .await()
    }

    @ImperiumCommand(["say"])
    @ServerSide
    suspend fun onServerMessageCommand(sender: CommandSender, @Greedy message: String) {
        if (!Vars.state.isGame) {
            sender.error("Not hosting. Host a game first.")
            return
        }

        val forServer = FlexAPI.get().messages.pump(MessageContext(sender.audience, sender.audience, message)).await()
        if (forServer.isBlank()) {
            return
        }

        sender.reply("&fi&lcServer: &fr&lw$forServer")
        messenger.broadcast(MindustryServerMessage(config.server.name, forServer, chat = true))

        FlexAPI.get()
            .messages
            .broadcast(
                Distributor.get().audienceProvider.server,
                Distributor.get().audienceProvider.players,
                message,
                "mindustry_chat_say",
            )
            .await()
    }
}
