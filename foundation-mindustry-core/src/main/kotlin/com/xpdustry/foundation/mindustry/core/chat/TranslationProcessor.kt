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
import com.xpdustry.foundation.common.misc.RateLimitException
import com.xpdustry.foundation.common.translator.Translator
import com.xpdustry.foundation.common.translator.UnsupportedLocaleException
import com.xpdustry.foundation.mindustry.core.processing.Processor
import fr.xpdustry.distributor.api.util.Players
import reactor.core.publisher.Mono
import java.time.Duration

class TranslationProcessor(private val translator: Translator) : Processor<ChatMessageContext, String> {
    override fun process(input: ChatMessageContext): Mono<String> {
        val sourceLocale = Players.getLocale(input.player)
        val targetLocale = Players.getLocale(input.target)
        val sourceMessage = Strings.stripColors(input.message)

        return translator.translate(sourceMessage, sourceLocale, targetLocale).timeout(Duration.ofSeconds(3L))
            .map { result ->
                if (sourceMessage == result) result else "${input.message} [lightgray]$result"
            }
            .onErrorResume(RateLimitException::class.java) {
                Mono.just(input.message)
            }
            .onErrorResume(UnsupportedLocaleException::class.java) {
                logger.debug("Warning: unsupported locale {} or {}", sourceLocale, targetLocale)
                Mono.just(input.message)
            }
            .onErrorResume { error ->
                logger.trace("Failed to translate the message '{}' from {} to {}", sourceMessage, sourceLocale, targetLocale, error)
                Mono.just(input.message)
            }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
