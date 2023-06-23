/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.mindustry.core.chat

import arc.util.Strings
import com.xpdustry.foundation.common.misc.LoggerDelegate
import com.xpdustry.foundation.common.translator.Translator
import com.xpdustry.foundation.mindustry.core.processing.Processor
import fr.xpdustry.distributor.api.util.Players
import reactor.core.publisher.Mono
import java.time.Duration

class TranslationProcessor(private val translator: Translator) : Processor<ChatMessageContext, String> {
    override fun process(input: ChatMessageContext): Mono<String> {
        val sourceLocale = Players.getLocale(input.sender)
        if (translator.isSupportedLanguage(sourceLocale).not()) {
            logger.debug("Warning: The locale {} is not supported by the chat translator", sourceLocale)
            return Mono.just(input.message)
        }

        val targetLocale = Players.getLocale(input.target)
        if (translator.isSupportedLanguage(targetLocale).not()) {
            logger.debug("Warning: The locale {} is not supported by the chat translator", targetLocale)
            return Mono.just(input.message)
        }

        val rawMessage = Strings.stripColors(input.message)
        return translator.translate(rawMessage, sourceLocale, targetLocale)
            .timeout(Duration.ofSeconds(3L))
            .map { translated ->
                if (rawMessage == translated) rawMessage else "${input.message} [lightgray]($translated)"
            }
            .switchIfEmpty(Mono.just(input.message))
            .onErrorResume { error ->
                logger.error("Failed to translate the message '{}' from {} to {}", rawMessage, sourceLocale, targetLocale, error)
                Mono.just(input.message)
            }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
