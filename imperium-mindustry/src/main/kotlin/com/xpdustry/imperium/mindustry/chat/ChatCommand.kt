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
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.asAudience
import kotlinx.coroutines.future.await
import mindustry.Vars
import mindustry.gen.Player
import org.incendo.cloud.annotation.specifier.Greedy

class ChatCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val messenger = instances.get<Messenger>()
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
        messenger.publish(MindustryServerMessage(config.server.name, forServer, chat = true))

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
