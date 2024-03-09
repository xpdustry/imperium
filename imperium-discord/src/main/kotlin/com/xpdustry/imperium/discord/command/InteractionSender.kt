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

import com.xpdustry.imperium.discord.misc.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder

sealed class InteractionSender<T> where T : IReplyCallback {
    val jda: JDA
        get() = interaction.jda

    val member: Member
        get() = interaction.member!!

    abstract val interaction: T

    suspend fun respond(block: suspend MessageCreateBuilder.() -> Unit): Message =
        interaction.hook.sendMessage(MessageCreateBuilder().apply { block() }.build()).await()

    suspend fun respond(message: String): Message = interaction.hook.sendMessage(message).await()

    suspend fun respond(vararg embeds: MessageEmbed): Message =
        interaction.hook.sendMessageEmbeds(embeds.toList()).await()

    class Slash(private val event: SlashCommandInteraction) :
        InteractionSender<SlashCommandInteraction>() {

        override val interaction: SlashCommandInteraction
            get() = event
    }

    class Button(private val event: ButtonInteraction) : InteractionSender<ButtonInteraction>() {
        val message: Message
            get() = event.message

        override var interaction: ButtonInteraction = event
    }
}
