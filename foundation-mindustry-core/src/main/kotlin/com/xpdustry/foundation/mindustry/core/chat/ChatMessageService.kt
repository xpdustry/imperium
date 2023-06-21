/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.mindustry.core.chat

import arc.Events
import arc.util.CommandHandler.ResponseType
import arc.util.Log
import arc.util.Time
import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.kotlin.extension.buildAndRegister
import com.google.inject.Inject
import com.google.inject.name.Named
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.translator.Translator
import com.xpdustry.foundation.mindustry.core.command.FoundationPluginCommandManager
import com.xpdustry.foundation.mindustry.core.misc.MindustryScheduler
import fr.xpdustry.distributor.api.command.argument.PlayerArgument
import fr.xpdustry.distributor.api.util.Priority
import mindustry.Vars
import mindustry.game.EventType.PlayerChatEvent
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.SendChatMessageCallPacket
import mindustry.net.Administration
import mindustry.net.Packets.KickReason
import mindustry.net.ValidateException
import reactor.core.publisher.Mono

class ChatMessageService @Inject constructor(
    private val pipeline: ChatMessagePipeline,
    private val translator: Translator,
    @Named("client")
    private val clientCommandManager: FoundationPluginCommandManager,
) : FoundationListener {
    override fun onFoundationInit() {
        pipeline.register("translator", Priority.LOW, TranslationProcessor(translator))
        pipeline.register("fuck", Priority.HIGH) { Mono.just(it.message.replace(Regex("fuck"), "****")) }

        // Intercept chat messages, so they go through the async processing pipeline
        Vars.net.handleServer(SendChatMessageCallPacket::class.java) { con, packet ->
            interceptChatMessage(con.player, packet.message, pipeline)
        }

        clientCommandManager.buildAndRegister("t") {
            commandDescription("Send a message to your team.")
            argument(StringArgument.greedy("message"))
            handler {
                val sender = it.sender.player
                val normalized: String = Vars.netServer.admins.filterMessage(it.sender.player, it.get("message"))
                    ?: return@handler

                Groups.player.each { target ->
                    if (target.team() != it.sender.player.team()) return@each
                    pipeline.build(ChatMessageContext(it.sender.player, target, normalized))
                        .publishOn(MindustryScheduler)
                        .subscribe { result ->
                            target.sendMessage(
                                "[#${sender.team().color}]<T> ${Vars.netServer.chatFormatter.format(sender, result)}",
                                sender,
                                result,
                            )
                        }
                }
            }
        }

        clientCommandManager.buildAndRegister("w") {
            commandDescription("Send a private message to a player.")
            argument(PlayerArgument.of("player"))
            argument(StringArgument.greedy("message"))
            handler {
                val sender = it.sender.player
                val target = it.get<Player>("player")
                val normalized: String = Vars.netServer.admins.filterMessage(it.sender.player, it.get("message"))
                    ?: return@handler

                pipeline.build(ChatMessageContext(it.sender.player, target, normalized))
                    .publishOn(MindustryScheduler)
                    .subscribe { result ->
                        target.sendMessage(
                            "[gray]<W>[] ${Vars.netServer.chatFormatter.format(sender, result)}",
                            sender,
                            result,
                        )
                    }
            }
        }
    }
}

private fun interceptChatMessage(player: Player, message: String, pipeline: ChatMessagePipeline) {
    // do not receive chat messages from clients that are too young or not registered
    if (Time.timeSinceMillis(player.con.connectTime) < 500 || !player.con.hasConnected || !player.isAdded) return

    // detect and kick for foul play
    if (!player.con.chatRate.allow(2000, Administration.Config.chatSpamLimit.num())) {
        player.con.kick(KickReason.kick)
        Vars.netServer.admins.blacklistDos(player.con.address)
        return
    }

    if (message.length > Vars.maxTextLength) {
        throw ValidateException(player, "Player has sent a message above the text limit.")
    }

    var normalized: String? = message.replace("\n", "")

    Events.fire(PlayerChatEvent(player, normalized))

    // log commands before they are handled
    if (normalized!!.startsWith(Vars.netServer.clientCommands.getPrefix())) {
        // log with brackets
        Log.info("<&fi@: @&fr>", "&lk" + player.plainName(), "&lw$normalized")
    }

    // check if it's a command
    val response = Vars.netServer.clientCommands.handleMessage(normalized, player)

    if (response.type == ResponseType.noCommand) { // no command to handle
        normalized = Vars.netServer.admins.filterMessage(player, normalized)
        // suppress chat message if it's filtered out
        if (normalized == null) {
            return
        }

        // BEGIN FOUNDATION
        Groups.player.each { target ->
            pipeline.build(ChatMessageContext(player, target, normalized)).publishOn(MindustryScheduler).subscribe { result ->
                if (target == player) {
                    // server console logging
                    Log.info("&fi@: @", "&lc" + player.plainName(), "&lw$result")
                }
                // invoke event for all clients but also locally
                // this is required so other clients get the correct name even if they don't know who's sending it yet
                Call.sendMessage(Vars.netServer.chatFormatter.format(player, result), result, player)
            }
        }
    } else {
        // a command was sent, now get the output
        if (response.type != ResponseType.valid) {
            val text = Vars.netServer.invalidHandler.handle(player, response)
            if (text != null) {
                player.sendMessage(text)
            }
        }
    }
}
