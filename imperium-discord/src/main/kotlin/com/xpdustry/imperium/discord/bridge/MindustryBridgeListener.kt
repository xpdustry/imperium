// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.bridge

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.BridgeChatMessage
import com.xpdustry.imperium.common.bridge.MindustryPlayerMessage
import com.xpdustry.imperium.common.bridge.MindustryServerMessage
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.discord.misc.addSuspendingEventListener
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class MindustryBridgeListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val messenger = instances.get<MessageService>()
    private val config = instances.get<ImperiumConfig>()

    override fun onImperiumInit() {
        discord.jda.addSuspendingEventListener<MessageReceivedEvent> { event ->
            if (event.isWebhookMessage || event.message.author.isBot || event.message.author.isSystem) {
                return@addSuspendingEventListener
            }
            val channel = (event.channel as? TextChannel) ?: return@addSuspendingEventListener
            if (
                channel.parentCategoryIdLong != 0L && channel.parentCategoryIdLong == config.discord.categories.liveChat
            ) {
                messenger.broadcast(
                    BridgeChatMessage(
                        channel.name,
                        event.message.member?.nickname ?: event.message.author.name,
                        event.author.idLong,
                        event.message.contentStripped,
                    )
                )
            }
        }

        messenger.subscribe<MindustryPlayerMessage> { message ->
            val channel = getLiveChatChannel(message.server) ?: return@subscribe
            val text =
                when (val action = message.action) {
                    is MindustryPlayerMessage.Action.Join ->
                        ":green_square: **${message.player}** has joined the server."
                    is MindustryPlayerMessage.Action.Quit -> ":red_square: **${message.player}** has left the server."
                    is MindustryPlayerMessage.Action.Chat -> ":blue_square: **${message.player}**: ${action.message}"
                }
            channel.sendMessage(text).setAllowedMentions(emptySet()).await()
        }

        messenger.subscribe<MindustryServerMessage> { message ->
            val channel = getLiveChatChannel(message.server) ?: return@subscribe
            val text = buildString {
                append(":purple_square: ")
                if (message.chat) append("**${message.server}**: ")
                append(message.message)
            }
            channel.sendMessage(text).setAllowedMentions(emptySet()).await()
        }
    }

    private suspend fun getLiveChatChannel(server: String): TextChannel? {
        val category = discord.getMainServer().getCategoryById(config.discord.categories.liveChat)
        if (category == null) {
            LOGGER.error("Live chat category is not defined.")
            return null
        }
        val channel =
            category.channels.find { it.name == server }
                ?: discord.getMainServer().createTextChannel(server, category).await()
        if (channel !is TextChannel) {
            LOGGER.error("Channel ${channel.name} (${channel.id}) is not a text channel")
            return null
        }
        return channel
    }

    companion object {
        private val LOGGER = logger<MindustryBridgeListener>()
    }
}
