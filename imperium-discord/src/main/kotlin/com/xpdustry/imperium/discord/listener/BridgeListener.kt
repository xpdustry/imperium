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
package com.xpdustry.imperium.discord.listener

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.BridgeChatMessage
import com.xpdustry.imperium.common.bridge.MindustryPlayerMessage
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.filterAndCast
import com.xpdustry.imperium.common.misc.switchIfEmpty
import com.xpdustry.imperium.common.misc.toValueMono
import com.xpdustry.imperium.discord.misc.toSnowflake
import com.xpdustry.imperium.discord.service.DiscordService
import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.entity.channel.Category
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.spec.CategoryCreateSpec
import discord4j.core.spec.MessageCreateSpec
import discord4j.core.spec.TextChannelCreateSpec
import discord4j.rest.util.AllowedMentions
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.jvm.optionals.getOrNull

// TODO Discord4j is so ugly, add extensions methods when the codebase will be a little bigger
class BridgeListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val discord = instances.get<DiscordService>()
    private val messenger = instances.get<Messenger>()
    override fun onImperiumInit() {
        discord.gateway.on(MessageCreateEvent::class.java)
            .filter { it.message.author.isPresent && !it.message.author.get().isBot }
            .filterWhen { event ->
                discord.getMainGuild().map { it.id == event.guildId.getOrNull() }
            }
            .map(MessageCreateEvent::getMessage)
            .flatMap { message ->
                message.channel.filterAndCast(TextChannel::class)
                    .filterWhen { channel ->
                        getLiveChatCategoryId().map { id -> channel.categoryId.getOrNull() == id }
                    }
                    .flatMap { channel ->
                        messenger.publish(
                            BridgeChatMessage(channel.name, message.author.get().username, message.content),
                        )
                    }
            }
            .subscribe()

        messenger.on(MindustryPlayerMessage.Join::class)
            .handleMindustryServerMessage()
        messenger.on(MindustryPlayerMessage.Quit::class)
            .handleMindustryServerMessage()
        messenger.on(MindustryPlayerMessage.Chat::class)
            .handleMindustryServerMessage()
    }

    private fun getLiveChatCategoryId(): Mono<Snowflake> = config.discord.categories.liveChat?.toSnowflake()?.toValueMono()
        ?: discord.getMainGuild().flatMap { guild ->
            guild.channels.filter { it is Category && it.name.lowercase() == "live chat" }
                .next()
                .cast(Category::class.java)
                .switchIfEmpty { guild.createCategory(CategoryCreateSpec.of("Live Chat")) }
                .map { it.id }
        }

    private fun <M : MindustryPlayerMessage> Flux<M>.handleMindustryServerMessage(): Disposable = flatMap { message ->
        discord.getMainGuild().flatMap { guild ->
            guild.channels.filterAndCast(TextChannel::class)
                .filter { it.name == message.serverName }
                .filterWhen { channel ->
                    getLiveChatCategoryId().map { channel.categoryId.getOrNull() == it }
                }
                .next()
                .switchIfEmpty {
                    getLiveChatCategoryId().flatMap { categoryId ->
                        guild.createTextChannel(
                            TextChannelCreateSpec.builder()
                                .name(message.serverName)
                                .parentId(categoryId)
                                .build(),
                        )
                    }
                }
        }
            .flatMap {
                it.createMessage(
                    MessageCreateSpec.builder()
                        .content(message.toDiscordMessage())
                        .allowedMentions(AllowedMentions.suppressAll())
                        .build(),
                )
            }
    }.subscribe()

    private fun MindustryPlayerMessage.toDiscordMessage(): String = when (this) {
        is MindustryPlayerMessage.Join -> ":green_square: **${player.name}** has joined the server."
        is MindustryPlayerMessage.Quit -> ":red_square: **${player.name}** has left the server."
        is MindustryPlayerMessage.Chat -> ":blue_square: **${player.name}**: $message"
    }
}
