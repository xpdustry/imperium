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

import arc.util.CommandHandler.ResponseType
import arc.util.Log
import arc.util.Time
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.annotation.Greedy
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toBase62
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.showInfoMessage
import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.util.Priority
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType.PlayerChatEvent
import mindustry.gen.Player
import mindustry.gen.SendChatMessageCallPacket
import mindustry.net.Administration
import mindustry.net.Packets.KickReason
import mindustry.net.ValidateException

class ChatMessageListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val pipeline = instances.get<ChatMessagePipeline>()
    private val punishments = instances.get<PunishmentManager>()

    override fun onImperiumInit() {
        // Intercept chat messages, so they go through the async processing pipeline
        Vars.net.handleServer(SendChatMessageCallPacket::class.java) { con, packet ->
            if (con.player == null || packet.message == null) return@handleServer
            interceptChatMessage(con.player, packet.message, pipeline)
        }

        // I don't know why but Foo client appends invisible characters to the end of messages,
        // this is very annoying for the discord bridge.
        pipeline.register("anti-foo-sign", Priority.HIGHEST) { context ->
            val msg = context.message
            // https://github.com/mindustry-antigrief/mindustry-client/blob/23025185c20d102f3fbb9d9a4c20196cc871d94b/core/src/mindustry/client/communication/InvisibleCharCoder.kt#L14
            if (msg.takeLast(2).all { (0xF80 until 0x107F).contains(it.code) }) msg.dropLast(2)
            else msg
        }

        pipeline.register("mute", Priority.HIGH) { ctx ->
            val muted =
                punishments
                    .findAllByAddress(ctx.sender.ip().toInetAddress())
                    .filter { !it.expired && it.type == Punishment.Type.MUTE }
                    .firstOrNull()
            if (muted != null) {
                if (ctx.target == ctx.sender) {
                    ctx.sender.showInfoMessage(
                        """
                        [scarlet]You can't talk. You are currently muted for '${muted.reason}'.
                        [orange]You can appeal this decision with the punishment id [cyan]${muted._id.toBase62()}[].
                        """
                            .trimIndent(),
                    )
                }
                return@register ""
            }
            ctx.message
        }
    }

    @Command(["t"])
    @ClientSide
    private suspend fun onTeamChatCommand(sender: CommandSender, @Greedy message: String) {
        val filtered1 = runMindustryThread {
            Vars.netServer.admins.filterMessage(sender.player, message)
        }
        if (filtered1.isNullOrBlank()) return

        for (target in Entities.PLAYERS) {
            if (sender.player.team() != target.team()) continue
            ImperiumScope.MAIN.launch {
                val filtered2 =
                    pipeline.pump(ChatMessageContext(sender.player, sender.player, message))
                if (filtered2.isBlank()) return@launch

                target.sendMessage(
                    "[#${sender.player.team().color}]<T> ${Vars.netServer.chatFormatter.format(sender.player, filtered2)}",
                    sender.player,
                    filtered2,
                )
            }
        }
    }

    @Command(["w"])
    @ClientSide
    private suspend fun onWhisperCommand(
        sender: CommandSender,
        target: Player,
        @Greedy message: String
    ) {
        val filtered1 = runMindustryThread {
            Vars.netServer.admins.filterMessage(sender.player, message)
        }
        if (filtered1.isNullOrBlank()) return
        val filtered2 = pipeline.pump(ChatMessageContext(sender.player, target, message))
        if (filtered2.isBlank()) return

        val formatted =
            "[gray]<W>[] ${Vars.netServer.chatFormatter.format(sender.player, filtered2)}"
        sender.player.sendMessage(formatted, sender.player, filtered2)
        target.sendMessage(formatted, sender.player, filtered2)
    }
}

private fun interceptChatMessage(sender: Player, message: String, pipeline: ChatMessagePipeline) {
    // do not receive chat messages from clients that are too young or not registered
    if (Time.timeSinceMillis(sender.con.connectTime) < 500 ||
        !sender.con.hasConnected ||
        !sender.isAdded)
        return

    // detect and kick for foul play
    if (!sender.con.chatRate.allow(2000, Administration.Config.chatSpamLimit.num())) {
        sender.con.kick(KickReason.kick)
        Vars.netServer.admins.blacklistDos(sender.con.address)
        return
    }

    if (message.length > Vars.maxTextLength) {
        throw ValidateException(sender, "Player has sent a message above the text limit.")
    }

    val escaped = message.replace("\n", "")

    DistributorProvider.get().eventBus.post(PlayerChatEvent(sender, escaped))

    // log commands before they are handled
    if (escaped.startsWith(Vars.netServer.clientCommands.getPrefix())) {
        // log with brackets
        Log.info("<&fi@: @&fr>", "&lk" + sender.plainName(), "&lw$escaped")
    }

    // check if it's a command
    val response = Vars.netServer.clientCommands.handleMessage(escaped, sender)

    if (response.type != ResponseType.noCommand) {
        // a command was sent, now get the output
        if (response.type != ResponseType.valid) {
            val text = Vars.netServer.invalidHandler.handle(sender, response)
            if (text != null) {
                sender.sendMessage(text)
            }
        }
        return
    }

    val filtered1 = Vars.netServer.admins.filterMessage(sender, escaped)
    if (filtered1.isNullOrBlank()) return

    // The null target represents the server, for logging and event purposes
    (Entities.PLAYERS + listOf(null)).forEach { target ->
        ImperiumScope.MAIN.launch {
            val filtered2 = pipeline.pump(ChatMessageContext(sender, target, filtered1))
            if (filtered2.isBlank()) return@launch
            target?.sendMessage(Vars.netServer.chatFormatter.format(sender, filtered2))
            if (target == null) {
                runMindustryThread {
                    Log.info(
                        "&fi@: @",
                        "&lc${sender.name().stripMindustryColors()}",
                        "&lw${filtered2.stripMindustryColors()}")
                    DistributorProvider.get()
                        .eventBus
                        .post(ProcessedPlayerChatEvent(sender, filtered2))
                }
            }
        }
    }
}
