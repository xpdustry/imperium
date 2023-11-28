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
package com.xpdustry.imperium.discord.bridge

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
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.discord.misc.identity
import com.xpdustry.imperium.discord.service.DiscordService
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.message.MessageBuilder
import org.javacord.api.entity.message.mention.AllowedMentionsBuilder

class BridgeListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val messenger = instances.get<Messenger>()
    private val config = instances.get<ServerConfig.Discord>()

    override fun onImperiumInit() {
        discord.getMainServer().addMessageCreateListener { event ->
            if (!event.message.author.isUser || event.message.content.isBlank()) {
                return@addMessageCreateListener
            }
            val channel =
                event.channel.asServerTextChannel().getOrNull() ?: return@addMessageCreateListener
            if (channel.category.getOrNull()?.id == config.categories.liveChat) {
                ImperiumScope.MAIN.launch {
                    messenger.publish(
                        BridgeChatMessage(
                            channel.name,
                            event.message.author.asUser().get().identity,
                            event.message.content))
                }
            }
        }

        messenger.consumer<MindustryPlayerMessage> { message ->
            val channel = getLiveChatChannel(message.server) ?: return@consumer
            val text =
                when (val action = message.action) {
                    is MindustryPlayerMessage.Action.Join ->
                        ":green_square: **${message.player.name}** has joined the server."
                    is MindustryPlayerMessage.Action.Quit ->
                        ":red_square: **${message.player.name}** has left the server."
                    is MindustryPlayerMessage.Action.Chat ->
                        ":blue_square: **${message.player.name}**: ${action.message}"
                }

            MessageBuilder().setAllowedMentions(NO_MENTIONS).setContent(text).send(channel).await()
        }

        messenger.consumer<MindustryServerMessage> { message ->
            val channel = getLiveChatChannel(message.server) ?: return@consumer
            val text = buildString {
                append(":purple_square: ")
                if (message.chat) append("**${message.server.name}**: ")
                append(message.message)
            }
            MessageBuilder().setAllowedMentions(NO_MENTIONS).setContent(text).send(channel).await()
        }
    }

    private suspend fun getLiveChatChannel(server: Identity.Server): ServerTextChannel? {
        val category =
            discord.getMainServer().getChannelCategoryById(config.categories.liveChat).get()
        val channel =
            category.channels.find { it.name == server.name }
                ?: discord
                    .getMainServer()
                    .createTextChannelBuilder()
                    .setCategory(category)
                    .setName(server.name)
                    .create()
                    .await()
        val textChannel = channel.asServerTextChannel().getOrNull()
        if (textChannel == null) {
            logger.error("Channel ${channel.name} (${channel.id}) is not a text channel")
            return null
        }
        return textChannel
    }

    companion object {
        private val logger by LoggerDelegate()
        private val NO_MENTIONS =
            AllowedMentionsBuilder()
                .setMentionEveryoneAndHere(false)
                .setMentionRoles(false)
                .setMentionUsers(false)
                .build()
    }
}
