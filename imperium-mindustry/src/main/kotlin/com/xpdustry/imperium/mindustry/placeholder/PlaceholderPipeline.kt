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
import com.xpdustry.imperium.mindustry.processing.AbstractProcessorPipeline
import com.xpdustry.imperium.mindustry.processing.ProcessorPipeline
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mindustry.gen.Player

data class PlaceholderContext(val player: Player, val message: String)

interface PlaceholderPipeline : ProcessorPipeline<PlaceholderContext, String>

class SimplePlaceholderManager : PlaceholderPipeline, AbstractProcessorPipeline<PlaceholderContext, String>() {
    override suspend fun pump(context: PlaceholderContext): String = withContext(ImperiumScope.MAIN.coroutineContext) {
        PLACEHOLDER_REGEX.findAll(context.message).map { it.groupValues[1] }.toSet()
            .map { placeholder ->
                async {
                    val parts = placeholder.split("_", limit = 2)
                    val processor = processor(parts[0])
                        ?: return@async null
                    val query = parts.getOrNull(1) ?: ""
                    try {
                        placeholder to processor.process(PlaceholderContext(context.player, query))
                    } catch (error: Exception) {
                        logger.error("Failed to process placeholder '{}'", placeholder, error)
                        null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
            .fold(context.message) { message, result -> message.replace("%${result.first}%", result.second) }
    }

    companion object {
        private val logger by LoggerDelegate()
        private val PLACEHOLDER_REGEX = Regex("%([^%]+)%")
    }
}
