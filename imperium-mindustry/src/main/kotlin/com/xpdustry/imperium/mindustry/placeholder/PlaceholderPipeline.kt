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
package com.xpdustry.imperium.mindustry.placeholder

import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.mindustry.processing.AbstractProcessorPipeline
import com.xpdustry.imperium.mindustry.processing.ProcessorPipeline
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

data class PlaceholderContext(val subject: Identity, val query: String)

interface PlaceholderPipeline : ProcessorPipeline<PlaceholderContext, String>

fun invalidQueryError(query: String): Nothing =
    throw IllegalArgumentException("Invalid query: $query")

class SimplePlaceholderPipeline :
    PlaceholderPipeline, AbstractProcessorPipeline<PlaceholderContext, String>("placeholder") {
    override suspend fun pump(context: PlaceholderContext): String =
        withContext(ImperiumScope.MAIN.coroutineContext) {
            extractPlaceholders(context.query)
                .map { placeholder ->
                    async {
                        val parts = placeholder.split(":", limit = 2)
                        val processor = processor(parts[0]) ?: return@async null
                        val query = parts.getOrNull(1) ?: ""
                        try {
                            placeholder to
                                processor.process(PlaceholderContext(context.subject, query))
                        } catch (error: Exception) {
                            logger.error("Failed to process placeholder '{}'", placeholder, error)
                            null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .fold(context.query) { message, (placeholder, result) ->
                    message.replace("%$placeholder%", result)
                }
        }

    private fun extractPlaceholders(query: String): Set<String> {
        val placeholders = mutableSetOf<String>()
        var index = 0
        while (index < query.length) {
            if (query[index] == '%') {
                val start = index
                do {
                    index++
                } while (index < query.length && query[index] != '%')
                if (index == query.length) {
                    logger.warn("Invalid placeholder query: {}", query)
                    break
                } else if (index == start + 1) {
                    index++
                    continue
                }
                placeholders.add(query.substring(start + 1, index))
            }
            index++
        }
        return placeholders
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
