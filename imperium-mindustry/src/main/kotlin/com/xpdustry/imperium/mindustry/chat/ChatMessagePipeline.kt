/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2024  Xpdustry
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
 *
 */
package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.mindustry.processing.AbstractProcessorPipeline
import com.xpdustry.imperium.mindustry.processing.ProcessorPipeline
import mindustry.gen.Player

data class ChatMessageContext(
    val sender: Player?,
    val target: Player?,
    val message: String,
)

interface ChatMessagePipeline : ProcessorPipeline<ChatMessageContext, String>

class SimpleChatMessagePipeline :
    ChatMessagePipeline, AbstractProcessorPipeline<ChatMessageContext, String>("chat-message") {
    override suspend fun pump(context: ChatMessageContext): String {
        var result = context.message
        for (processor in processors) {
            result =
                try {
                    processor.process(context.copy(message = result))
                } catch (error: Throwable) {
                    val author =
                        if (context.sender != null)
                            "player ${context.sender.name().stripMindustryColors()}"
                        else "server"
                    logger.error("Error while processing chat message of $author", error)
                    result
                }
            if (result.isEmpty()) break
        }
        return result
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
