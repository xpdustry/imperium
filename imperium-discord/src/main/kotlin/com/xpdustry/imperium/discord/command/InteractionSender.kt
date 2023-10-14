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
package com.xpdustry.imperium.discord.command

import kotlinx.coroutines.future.await
import org.javacord.api.DiscordApi
import org.javacord.api.entity.channel.TextChannel
import org.javacord.api.entity.message.Message
import org.javacord.api.entity.message.embed.EmbedBuilder
import org.javacord.api.entity.user.User
import org.javacord.api.event.interaction.ButtonClickEvent
import org.javacord.api.event.interaction.SlashCommandCreateEvent
import org.javacord.api.interaction.ButtonInteraction
import org.javacord.api.interaction.InteractionBase
import org.javacord.api.interaction.SlashCommandInteraction
import org.javacord.api.interaction.callback.ExtendedInteractionMessageBuilderBase

sealed class InteractionSender {
    val discord: DiscordApi
        get() = interaction.api

    val user: User
        get() = interaction.user

    protected abstract val interaction: InteractionBase

    suspend fun respond(
        block: suspend ExtendedInteractionMessageBuilderBase<*>.() -> Unit
    ): Message = interaction.createFollowupMessageBuilder().apply { block() }.send().await()

    suspend fun respond(message: String): Message =
        interaction.createFollowupMessageBuilder().setContent(message).send().await()

    suspend fun respond(vararg embed: EmbedBuilder): Message =
        interaction.createFollowupMessageBuilder().addEmbeds(*embed).send().await()

    class Slash(private val event: SlashCommandCreateEvent) : InteractionSender() {
        val channel: TextChannel
            get() = event.slashCommandInteraction.channel.get()

        override val interaction: SlashCommandInteraction
            get() = event.slashCommandInteraction
    }

    class Button(private val event: ButtonClickEvent) : InteractionSender() {
        val message: Message
            get() = event.buttonInteraction.message

        override var interaction: ButtonInteraction = event.buttonInteraction
    }
}
