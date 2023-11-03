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

import arc.util.Strings
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.translator.Translator
import com.xpdustry.imperium.common.translator.TranslatorResult
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.util.Players
import fr.xpdustry.distributor.api.util.Priority
import kotlinx.coroutines.withTimeoutOrNull
import mindustry.game.EventType.PlayerJoin

class ChatTranslatorListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val translator = instances.get<Translator>()
    private val pipeline = instances.get<ChatMessagePipeline>()

    override fun onImperiumInit() {
        pipeline.register("translator", Priority.LOW) { context ->
            val sourceLocale = context.sender?.let(Players::getLocale) ?: config.language
            val targetLocale = context.target?.let(Players::getLocale) ?: config.language
            val rawMessage = Strings.stripColors(context.message)

            val result =
                withTimeoutOrNull(3000L) {
                    translator.translate(rawMessage, sourceLocale, targetLocale)
                }

            when (result) {
                is TranslatorResult.UnsupportedLanguage ->
                    logger.debug(
                        "Warning: The locale {} is not supported by the chat translator",
                        result.locale)
                is TranslatorResult.Failure ->
                    logger.error(
                        "Failed to translate the message '{}' from {} to {}",
                        rawMessage,
                        sourceLocale,
                        targetLocale,
                        result.exception)
                is TranslatorResult.RateLimited ->
                    logger.debug("Warning: The chat translator is rate limited")
                null ->
                    logger.error(
                        "Failed to translate the message '{}' from {} to {} due to timeout",
                        rawMessage,
                        sourceLocale,
                        targetLocale)
                is TranslatorResult.Success -> {
                    return@register if (rawMessage.lowercase(sourceLocale) ==
                        result.text.lowercase(targetLocale))
                        rawMessage
                    else "${context.message} [lightgray](${result.text})"
                }
            }

            context.message
        }
    }

    @EventHandler
    fun onPlayerConnect(event: PlayerJoin) {
        if (translator.isSupportedLanguage(Players.getLocale(event.player))) {
            event.player.sendMessage(
                "[green]The chat translator supports your language, you can talk in your native tongue!")
        } else {
            event.player.sendMessage(
                "[scarlet]Warning, your language is not supported by the chat translator. Please talk in english.")
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
