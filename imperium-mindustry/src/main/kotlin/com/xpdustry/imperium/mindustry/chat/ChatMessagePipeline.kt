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

import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.processing.AbstractProcessorPipeline
import com.xpdustry.imperium.mindustry.processing.ProcessorPipeline
import mindustry.gen.Player
import reactor.core.publisher.Mono

data class ChatMessageContext(
    val sender: Player,
    val target: Player,
    val message: String,
)

interface ChatMessagePipeline : ProcessorPipeline<ChatMessageContext, String>

class SimpleChatMessagePipeline : ChatMessagePipeline, AbstractProcessorPipeline<ChatMessageContext, String>() {

    override fun build(context: ChatMessageContext): Mono<String> =
        build0(context, 0)

    private fun build0(context: ChatMessageContext, index: Int): Mono<String> {
        if (index >= processors.size) {
            return Mono.just(context.message)
        }
        return Mono.defer { processors[index].process(context) }
            .onErrorResume { error ->
                logger.error("Error while processing chat message for player ${context.sender.name()}", error)
                Mono.empty()
            }
            .flatMap {
                if (it.isEmpty()) Mono.empty() else build0(context.copy(message = it), index.inc())
            }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
