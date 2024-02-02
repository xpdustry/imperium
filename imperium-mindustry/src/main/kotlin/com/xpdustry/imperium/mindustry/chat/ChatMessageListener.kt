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
import arc.util.Time
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.bridge.MindustryServerMessage
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.annotation.Greedy
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.MINDUSTRY_BLUE_COLOR
import com.xpdustry.imperium.common.misc.containsLink
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toHexString
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.showInfoMessage
import com.xpdustry.imperium.mindustry.placeholder.PlaceholderContext
import com.xpdustry.imperium.mindustry.placeholder.PlaceholderPipeline
import com.xpdustry.imperium.mindustry.placeholder.invalidQueryError
import com.xpdustry.imperium.mindustry.processing.registerCaching
import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.util.Priority
import java.text.DecimalFormat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType.PlayerChatEvent
import mindustry.gen.Iconc
import mindustry.gen.Player
import mindustry.gen.SendChatMessageCallPacket
import mindustry.net.Administration
import mindustry.net.Packets.KickReason
import mindustry.net.ValidateException

private val ROOT_LOGGER = logger("ROOT")

class ChatMessageListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val chatMessagePipeline = instances.get<ChatMessagePipeline>()
    private val placeholderPipeline = instances.get<PlaceholderPipeline>()
    private val accounts = instances.get<AccountManager>()
    private val punishments = instances.get<PunishmentManager>()
    private val config = instances.get<ServerConfig.Mindustry>()
    private val messenger = instances.get<Messenger>()

    override fun onImperiumInit() {
        // Intercept chat messages, so they go through the async processing pipeline
        Vars.net.handleServer(SendChatMessageCallPacket::class.java) { con, packet ->
            if (con.player == null || packet.message == null) return@handleServer
            interceptChatMessage(con.player, packet.message)
        }

        // I don't know why but Foo client appends invisible characters to the end of messages,
        // this is very annoying for the discord bridge.
        chatMessagePipeline.register("anti-foo-sign", Priority.HIGHEST) { context ->
            val msg = context.message
            // https://github.com/mindustry-antigrief/mindustry-client/blob/23025185c20d102f3fbb9d9a4c20196cc871d94b/core/src/mindustry/client/communication/InvisibleCharCoder.kt#L14
            if (msg.takeLast(2).all { (0xF80 until 0x107F).contains(it.code) }) msg.dropLast(2)
            else msg
        }

        chatMessagePipeline.register("mute", Priority.HIGH) { ctx ->
            if (ctx.sender == null) {
                return@register ctx.message
            }
            val muted =
                punishments.findAllByIdentity(ctx.sender.identity).firstOrNull {
                    !it.expired && it.type == Punishment.Type.MUTE
                }
            if (muted != null) {
                if (ctx.target == ctx.sender) {
                    ctx.sender.showInfoMessage(
                        """
                        [scarlet]You can't talk. You are currently muted for '${muted.reason}'.
                        [orange]You can appeal this decision with the punishment id [cyan]${muted.snowflake}[].
                        """
                            .trimIndent(),
                    )
                }
                return@register ""
            }
            ctx.message
        }

        chatMessagePipeline.register("anti-links", Priority.NORMAL) { ctx ->
            if (ctx.sender == null || !ctx.message.containsLink()) {
                return@register ctx.message
            } else {
                if (ctx.target == ctx.sender) {
                    ctx.sender.sendMessage(
                        "[scarlet]You can't send discord invitations or links in the chat.")
                }
                return@register ""
            }
        }

        val chaoticHourFormat = DecimalFormat("000")
        placeholderPipeline.registerCaching("subject_playtime", 10.seconds, ::getContextKey) {
            (subject, query) ->
            val playtime =
                when (subject) {
                    is Identity.Mindustry -> accounts.findByIdentity(subject)?.playtime
                    is Identity.Discord -> accounts.findByDiscord(subject.id)?.playtime
                    else -> null
                }
                    ?: return@registerCaching ""
            when (query) {
                "hours" -> playtime.inWholeHours.toString()
                "chaotic" -> chaoticHourFormat.format(playtime.inWholeHours)
                else -> invalidQueryError(query)
            }
        }

        placeholderPipeline.register("subject_name") { (subject, query) ->
            when (query) {
                "plain" -> subject.name
                "display" ->
                    when (subject) {
                        is Identity.Mindustry -> subject.displayName
                        else -> subject.name
                    }
                else -> invalidQueryError(query)
            }
        }

        placeholderPipeline.registerCaching("subject_color", 10.seconds, ::getContextKey) {
            (subject, query) ->
            if (query != "hex") {
                invalidQueryError(query)
            }
            when (subject) {
                is Identity.Mindustry ->
                    Entities.getPlayersAsync()
                        .find { it.uuid() == subject.uuid }
                        ?.color
                        ?.toString()
                        ?.let { "#$it" }
                        ?: MINDUSTRY_ORANGE_COLOR.toHexString()
                is Identity.Discord -> MINDUSTRY_BLUE_COLOR.toHexString()
                else -> config.color.toHexString()
            }
        }

        // TODO
        //   - Move to dedicated class
        //   - Better processing perhaps
        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(1.seconds)
                val result =
                    Entities.getPlayersAsync().map { player ->
                        player to
                            try {
                                placeholderPipeline.pump(
                                    PlaceholderContext(
                                        player.identity, config.templates.playerName))
                            } catch (e: Exception) {
                                LOGGER.error("Failed to format name of player {}", player.uuid(), e)
                                "[#${player.color}]${player.info.lastName}"
                            }
                    }
                runMindustryThread { result.forEach { (player, name) -> player.name(name) } }
            }
        }
    }

    private fun getContextKey(context: PlaceholderContext): String =
        when (context.subject) {
            is Identity.Server -> "server"
            is Identity.Mindustry -> context.subject.uuid + ":" + context.subject.usid
            is Identity.Discord -> context.subject.id.toString()
        }

    @Command(["t"])
    @ClientSide
    private suspend fun onTeamChatCommand(sender: CommandSender, @Greedy message: String) {
        val filtered1 = runMindustryThread {
            Vars.netServer.admins.filterMessage(sender.player, message)
        }
        if (filtered1.isNullOrBlank()) return

        for (target in Entities.getPlayersAsync()) {
            if (sender.player.team() != target.team()) continue
            ImperiumScope.MAIN.launch {
                val filtered2 =
                    chatMessagePipeline.pump(
                        ChatMessageContext(sender.player, sender.player, message))
                if (filtered2.isBlank()) return@launch

                target.sendMessage(
                    "[#${sender.player.team().color}]${getChatPrefix("T")} ${getChatFormat(sender.player.identity, filtered2)}",
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
        if (sender.player == target) {
            sender.sendWarning("You can't whisper to yourself.")
            return
        }
        val filtered1 = runMindustryThread {
            Vars.netServer.admins.filterMessage(sender.player, message)
        }
        if (filtered1.isNullOrBlank()) return

        for (receiver in listOf(sender.player, target)) {
            val filtered2 =
                chatMessagePipeline.pump(ChatMessageContext(sender.player, receiver, message))
            if (filtered2.isBlank()) continue
            val formatted =
                "[gray]${getChatPrefix("W")}[] ${getChatFormat(sender.player.identity, filtered2)}"
            receiver.sendMessage(formatted, sender.player, filtered2)
        }
    }

    @Command(["say"])
    @ServerSide
    private suspend fun onServerMessageCommand(sender: CommandSender, @Greedy message: String) {
        if (!Vars.state.isGame) {
            sender.sendWarning("Not hosting. Host a game first.")
            return
        }

        // The null target represents the server, for logging purposes
        (Entities.getPlayersAsync() + listOf(null)).forEach { target ->
            ImperiumScope.MAIN.launch {
                val processed = chatMessagePipeline.pump(ChatMessageContext(null, target, message))
                if (processed.isBlank()) return@launch
                target?.sendMessage(
                    "[${config.color.toHexString()}]${getChatPrefix(Iconc.infoCircle.toString())} ${getChatFormat(config.identity, processed)}",
                    null,
                    processed)
                if (target == null) {
                    sender.sendMessage("&fi&lcServer: &fr&lw${processed.stripMindustryColors()}")
                    messenger.publish(
                        MindustryServerMessage(config.identity, processed, chat = true))
                }
            }
        }
    }

    private suspend fun getChatFormat(subject: Identity, message: String): String {
        return placeholderPipeline.pump(PlaceholderContext(subject, config.templates.chatFormat)) +
            " " +
            message
    }

    private fun getChatPrefix(prefix: String): String {
        return config.templates.chatPrefix.replace("%prefix%", prefix)
    }

    private fun interceptChatMessage(sender: Player, message: String) {
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
            val secured = if (escaped.startsWith("/login")) "/login" else escaped
            // log with brackets
            ROOT_LOGGER.info("<&fi{}: {}&fr>", "&lk${sender.plainName()}", "&lw$secured")
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
        (Entities.getPlayers() + listOf(null)).forEach { target ->
            ImperiumScope.MAIN.launch {
                val filtered2 =
                    chatMessagePipeline.pump(ChatMessageContext(sender, target, filtered1))
                if (filtered2.isBlank()) return@launch
                target?.sendMessage(getChatFormat(sender.identity, filtered2), sender, filtered2)
                if (target == null) {
                    ROOT_LOGGER.info(
                        "&fi{}: {}",
                        "&lc${sender.name().stripMindustryColors()}",
                        "&lw${filtered2.stripMindustryColors()}")
                    runMindustryThread {
                        DistributorProvider.get()
                            .eventBus
                            .post(ProcessedPlayerChatEvent(sender, filtered2))
                    }
                }
            }
        }
    }

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}
