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
package com.xpdustry.imperium.discord.interaction

import kotlinx.coroutines.future.await
import org.javacord.api.DiscordApi
import org.javacord.api.entity.channel.TextChannel
import org.javacord.api.entity.message.Message
import org.javacord.api.entity.message.MessageFlag
import org.javacord.api.entity.message.embed.EmbedBuilder
import org.javacord.api.entity.user.User
import org.javacord.api.event.interaction.ButtonClickEvent
import org.javacord.api.event.interaction.SlashCommandCreateEvent
import org.javacord.api.interaction.ButtonInteraction
import org.javacord.api.interaction.InteractionBase
import org.javacord.api.interaction.SlashCommandInteraction
import org.javacord.api.interaction.callback.ExtendedInteractionMessageBuilderBase

sealed class InteractionActor {
    val discord: DiscordApi get() = interaction.api
    val user: User get() = interaction.user
    protected abstract val interaction: InteractionBase

    suspend fun respond(block: suspend ExtendedInteractionMessageBuilderBase<*>.() -> Unit): Message =
        interaction.createFollowupMessageBuilder()
            .apply { block() }
            .send()
            .await()

    suspend fun respond(message: String, ephemeral: Boolean = true): Message =
        interaction.createFollowupMessageBuilder()
            .setContent(message)
            .apply { if (ephemeral) setFlags(MessageFlag.EPHEMERAL) }
            .send()
            .await()

    suspend fun respond(embed: EmbedBuilder, ephemeral: Boolean = true): Message =
        interaction.createFollowupMessageBuilder()
            .addEmbed(embed)
            .apply { if (ephemeral) setFlags(MessageFlag.EPHEMERAL) }
            .send()
            .await()

    class Slash(private val event: SlashCommandCreateEvent) : InteractionActor() {
        val channel: TextChannel get() = event.slashCommandInteraction.channel.get()
        override val interaction: SlashCommandInteraction get() = event.slashCommandInteraction
    }

    class Button(private val event: ButtonClickEvent, val payload: String?) : InteractionActor() {
        val message: Message get() = event.buttonInteraction.message
        override var interaction: ButtonInteraction = event.buttonInteraction
    }
}
