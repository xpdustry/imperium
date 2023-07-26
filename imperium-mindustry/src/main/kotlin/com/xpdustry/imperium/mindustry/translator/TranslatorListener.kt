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
package com.xpdustry.imperium.mindustry.translator

import arc.util.Strings
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.translator.Translator
import com.xpdustry.imperium.mindustry.chat.ChatMessagePipeline
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.util.Players
import fr.xpdustry.distributor.api.util.Priority
import mindustry.game.EventType.PlayerJoin
import reactor.core.publisher.Mono
import java.time.Duration

class TranslatorListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val translator: Translator = instances.get()
    private val pipeline: ChatMessagePipeline = instances.get()

    override fun onImperiumInit() {
        pipeline.register("translator", Priority.LOW) { context ->
            val sourceLocale = Players.getLocale(context.sender)
            if (translator.isSupportedLanguage(sourceLocale).not()) {
                logger.debug("Warning: The locale {} is not supported by the chat translator", sourceLocale)
                return@register Mono.just(context.message)
            }

            val targetLocale = Players.getLocale(context.target)
            if (translator.isSupportedLanguage(targetLocale).not()) {
                logger.debug("Warning: The locale {} is not supported by the chat translator", targetLocale)
                return@register Mono.just(context.message)
            }

            val rawMessage = Strings.stripColors(context.message)
            return@register translator.translate(rawMessage, sourceLocale, targetLocale)
                .timeout(Duration.ofSeconds(3L))
                .map { translated ->
                    if (rawMessage == translated) rawMessage else "${context.message} [lightgray]($translated)"
                }
                .switchIfEmpty(Mono.just(context.message))
                .onErrorResume { error ->
                    logger.error("Failed to translate the message '{}' from {} to {}", rawMessage, sourceLocale, targetLocale, error)
                    Mono.just(context.message)
                }
        }
    }

    @EventHandler
    fun onPlayerConnect(event: PlayerJoin) {
        if (translator.isSupportedLanguage(Players.getLocale(event.player))) {
            event.player.sendMessage("[green]The chat translator supports your language, you can talk in your native tongue!")
        } else {
            event.player.sendMessage("[scarlet]Warning, your language is not supported by the chat translator. Please talk in english.")
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
