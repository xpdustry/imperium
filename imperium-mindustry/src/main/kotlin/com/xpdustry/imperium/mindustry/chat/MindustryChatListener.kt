// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.chat

import arc.Core
import arc.util.CommandHandler.ResponseType
import arc.util.Strings
import arc.util.Time
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.audience.PlayerAudience
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.key.StandardKeys
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.flex.translator.RateLimitedException
import com.xpdustry.flex.translator.Translator
import com.xpdustry.flex.translator.UnsupportedLanguageException
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.containsLink
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.translation.SCARLET
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.SendChatMessageCallPacket
import mindustry.net.Administration
import mindustry.net.NetConnection
import mindustry.net.Packets.KickReason
import mindustry.net.ValidateException

@Inject
class MindustryChatListener(
    private val config: ImperiumConfig,
    private val formatter: MindustryAudienceFormatter,
    private val messages: MindustryMessagePipeline,
    private val translator: Translator,
    private val users: UserManager,
) : ImperiumApplication.Listener {
    private var nameJob: Job? = null

    override fun onImperiumInit() {
        registerProcessors()
        registerChatHook()

        if (config.mindustry.chat.joinMessages || config.mindustry.chat.quitMessages) {
            Administration.Config.showConnectMessages.set(false)
        }

        if (config.mindustry.chat.name.enabled) {
            nameJob =
                ImperiumScope.MAIN.launch {
                    while (isActive) {
                        updatePlayerNames()
                        delay(config.mindustry.chat.name.updateInterval)
                    }
                }
        }
    }

    override fun onImperiumExit() {
        nameJob?.cancel()
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        if (!config.mindustry.chat.joinMessages) {
            return
        }
        ROOT_LOGGER.info("&lb{}&fi&lk has connected. [&lb{}&fi&lk]", event.player.plainName(), event.player.uuid())
        sendConnectMessage("[accent]${event.player.info.plainLastName()} has connected.")
    }

    @EventHandler
    fun onPlayerQuit(event: EventType.PlayerLeave) {
        if (!config.mindustry.chat.quitMessages) {
            return
        }
        ROOT_LOGGER.info("&lb{}&fi&lk has disconnected. [&lb{}&fi&lk]", event.player.plainName(), event.player.uuid())
        sendConnectMessage("[accent]${event.player.info.plainLastName()} has disconnected.")
    }

    private fun registerProcessors() {
        messages.register("foo-sign-strip", Priority.HIGHEST) { context ->
            if (!config.mindustry.chat.fooClientCompatibility) {
                return@register context.message
            }
            val message = context.message
            if (message.length < 2) {
                return@register message
            }
            if (message.takeLast(2).all { it.code in 0xF80..0x107F }) {
                message.dropLast(2)
            } else {
                message
            }
        }

        messages.register("admin-filter", Priority.HIGH) { context ->
            if (
                context.sender is PlayerAudience && context.kind == MindustryMessageContext.Kind.CHAT && context.filter
            ) {
                runMindustryThread { Vars.netServer.admins.filterMessage(context.sender.player, context.message) ?: "" }
            } else {
                context.message
            }
        }

        messages.register("anti-links", Priority.NORMAL) { context ->
            if (
                context.filter &&
                    context.sender != Distributor.get().audienceProvider.server &&
                    context.message.containsLink()
            ) {
                context.sender.sendMessage(text("You can't send discord invitations or links in the chat.", SCARLET))
                ""
            } else {
                context.message
            }
        }

        messages.register("translator", Priority.LOW) { context -> translate(context) }
    }

    private fun registerChatHook() {
        Vars.net.handleServer(SendChatMessageCallPacket::class.java, ::interceptChatPacket)
    }

    private fun interceptChatPacket(connection: NetConnection, packet: SendChatMessageCallPacket) {
        if (connection.player == null || packet.message == null) {
            return
        }

        val audience = Distributor.get().audienceProvider.getPlayer(connection.player)
        var message = packet.message

        if (
            Vars.net.server() &&
                (Time.timeSinceMillis(audience.player.con().connectTime) < 500 ||
                    !audience.player.con().hasConnected ||
                    !audience.player.isAdded)
        ) {
            return
        }

        if (!audience.player.con().chatRate.allow(2000, Administration.Config.chatSpamLimit.num())) {
            audience.player.con().kick(KickReason.kick)
            Vars.netServer.admins.blacklistDos(audience.player.con().address)
            return
        }

        if (message.length > Vars.maxTextLength) {
            throw ValidateException(audience.player, "Player has sent a message above the text limit.")
        }

        message = message.replace("\n", "")
        Distributor.get().eventBus.post(EventType.PlayerChatEvent(audience.player, message))

        val prefix = Vars.netServer.clientCommands.getPrefix()
        ImperiumScope.MAIN.launch {
            val isCommand = message.startsWith(prefix)
            var forServer =
                messages.pump(
                    MindustryMessageContext(
                        audience,
                        Distributor.get().audienceProvider.server,
                        if (isCommand && message.length >= prefix.length) message.drop(prefix.length) else message,
                        filter = true,
                        kind =
                            if (isCommand) MindustryMessageContext.Kind.COMMAND else MindustryMessageContext.Kind.CHAT,
                    )
                )

            if (forServer.isBlank()) {
                return@launch
            }

            if (isCommand) {
                forServer = "$prefix$forServer"
                ROOT_LOGGER.info("<&fi{}: {}&fr>", "&lk${audience.player.plainName()}", "&lw$forServer")
                Core.app.post {
                    val response = Vars.netServer.clientCommands.handleMessage(forServer, audience.player)
                    when (response.type) {
                        ResponseType.valid -> Unit
                        else ->
                            Vars.netServer.invalidHandler
                                .handle(audience.player, response)
                                ?.let(audience.player::sendMessage)
                    }
                }
                return@launch
            }

            ROOT_LOGGER.info("&fi{}: {}", "&lc${audience.player.plainName()}", "&lw${Strings.stripColors(forServer)}")
            Core.app.post { Distributor.get().eventBus.post(MindustryPlayerChatEvent(audience, forServer)) }
            messages.broadcast(
                audience,
                Distributor.get().audienceProvider.players,
                message,
                MindustryMessageTemplate.CHAT,
            )
        }
    }

    private suspend fun translate(context: MindustryMessageContext): String {
        if (context.kind != MindustryMessageContext.Kind.CHAT) {
            return context.message
        }

        val raw = Strings.stripColors(context.message).lowercase()
        var sourceLocale = context.sender.metadata[StandardKeys.LOCALE] ?: Locale.getDefault()
        val targetLocale = context.target.metadata[StandardKeys.LOCALE] ?: Locale.getDefault()
        val muuid = context.sender.metadata[StandardKeys.MUUID]

        if (
            sourceLocale.language != targetLocale.language &&
                muuid != null &&
                users.getSetting(muuid.uuid, User.Setting.AUTOMATIC_LANGUAGE_DETECTION)
        ) {
            sourceLocale = Translator.AUTO_DETECT
        }

        return try {
            val result =
                withTimeout(3.seconds) { translator.translateDetecting(raw, sourceLocale, targetLocale).await().text }
            if (raw == result.lowercase()) {
                context.message
            } else {
                "${context.message} [lightgray]($result)"
            }
        } catch (_: RateLimitedException) {
            context.message
        } catch (_: UnsupportedLanguageException) {
            context.message
        } catch (error: Exception) {
            LOGGER.error("Failed to translate the message '{}' from {} to {}", raw, sourceLocale, targetLocale, error)
            context.message
        }
    }

    private suspend fun updatePlayerNames() {
        if (!Vars.state.isGame) {
            return
        }

        com.xpdustry.imperium.mindustry.misc.Entities.getPlayersAsync().forEach { player ->
            val result = formatter.formatName(player.asAudience)
            com.xpdustry.imperium.mindustry.misc.runMindustryThread {
                if (result.length > config.mindustry.chat.name.maximumNameSize) {
                    LOGGER.warn("Possible name overflow for player {} ({}), resetting.", player.name(), player.uuid())
                    player.name(player.info.lastName)
                } else if (result.isNotBlank()) {
                    player.name(result)
                } else {
                    LOGGER.warn("Processed name of player {} ({}) is blank.", player.name(), player.uuid())
                }
            }
        }
    }

    private fun sendConnectMessage(message: String) {
        Distributor.get()
            .audienceProvider
            .players
            .sendMessage(Distributor.get().mindustryComponentDecoder.decode(message))
    }

    companion object {
        private val LOGGER = logger<MindustryChatListener>()
        private val ROOT_LOGGER = logger("ROOT")
    }
}
