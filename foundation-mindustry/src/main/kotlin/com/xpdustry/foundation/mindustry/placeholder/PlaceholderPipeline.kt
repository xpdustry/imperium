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
package com.xpdustry.foundation.mindustry.placeholder

import com.xpdustry.foundation.common.misc.LoggerDelegate
import com.xpdustry.foundation.common.misc.toValueFlux
import com.xpdustry.foundation.mindustry.processing.AbstractProcessorPipeline
import com.xpdustry.foundation.mindustry.processing.ProcessorPipeline
import mindustry.gen.Player
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

data class PlaceholderContext(val player: Player, val message: String)

interface PlaceholderPipeline : ProcessorPipeline<PlaceholderContext, String>

class SimplePlaceholderManager : PlaceholderPipeline, AbstractProcessorPipeline<PlaceholderContext, String>() {

    override fun build(context: PlaceholderContext): Mono<String> =
        PLACEHOLDER_REGEX.findAll(context.message)
            .map { it.groupValues[1] }
            .toSet()
            .toValueFlux()
            .parallel()
            .runOn(Schedulers.parallel())
            .flatMap { placeholder ->
                val parts = placeholder.split("_", limit = 2)
                val processor = processor(parts[0])
                    ?: return@flatMap Mono.empty()
                val query = parts.getOrNull(1) ?: ""
                return@flatMap Mono.defer { processor.process(PlaceholderContext(context.player, query)) }
                    .onErrorResume { error ->
                        logger.error("Failed to process placeholder '{}'", placeholder, error)
                        Mono.empty()
                    }
                    .map { placeholder to it }
            }
            .sequential()
            .reduce(context.message) { message, result ->
                message.replace("%${result.first}%", result.second)
            }

    companion object {
        private val logger by LoggerDelegate()
        private val PLACEHOLDER_REGEX = Regex("%([^%]+)%")
    }
}
