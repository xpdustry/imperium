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

import com.xpdustry.foundation.common.misc.LoggerDelegate
import com.xpdustry.foundation.common.misc.toValueFlux
import com.xpdustry.foundation.mindustry.core.processing.AbstractProcessorPipeline
import com.xpdustry.foundation.mindustry.core.processing.ProcessorPipeline
import mindustry.gen.Player
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

data class ChatMessageContext(
    val player: Player,
    val target: Player,
    val message: String,
)

interface ChatMessagePipeline : ProcessorPipeline<ChatMessageContext, String>

class SimpleChatMessagePipeline : ChatMessagePipeline, AbstractProcessorPipeline<ChatMessageContext, String>() {

    // TODO: The reduce function is blocking, we need to figure out how to make it async
    override fun build(context: ChatMessageContext): Mono<String> =
        processors.toValueFlux()
            .publishOn(Schedulers.boundedElastic())
            .reduce(context) { ctx, processor ->
                if (ctx.message.isEmpty()) {
                    return@reduce ctx
                }
                return@reduce processor.process(ctx)
                    .onErrorResume { error ->
                        logger.error("Error while processing chat message for player ${ctx.player.name()}", error)
                        Mono.empty()
                    }
                    .switchIfEmpty(Mono.just(""))
                    .map { ctx.copy(message = it) }
                    .block()!!
            }
            .flatMap {
                if (it.message.isEmpty()) Mono.empty() else Mono.just(it.message)
            }

    companion object {
        private val logger by LoggerDelegate()
    }
}
