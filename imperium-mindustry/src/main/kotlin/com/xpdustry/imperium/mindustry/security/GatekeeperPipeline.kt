/*
 * Imperium, the software collection powering the Chaotic Neutral network.
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
 */
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.processing.AbstractProcessorPipeline
import com.xpdustry.imperium.mindustry.processing.ProcessorPipeline
import java.net.InetAddress
import kotlin.time.Duration
import kotlinx.coroutines.withContext

data class GatekeeperContext(val name: String, val uuid: String, val usid: String, val address: InetAddress)

sealed interface GatekeeperResult {
    data object Success : GatekeeperResult

    data class Failure(val reason: Component, val time: Duration = Duration.ZERO, val silent: Boolean = false) :
        GatekeeperResult {
        constructor(
            reason: String,
            time: Duration = Duration.ZERO,
            silent: Boolean = false,
        ) : this(Distributor.get().mindustryComponentDecoder.decode(reason), time, silent)
    }
}

interface GatekeeperPipeline : ProcessorPipeline<GatekeeperContext, GatekeeperResult>

class SimpleGatekeeperPipeline :
    GatekeeperPipeline, AbstractProcessorPipeline<GatekeeperContext, GatekeeperResult>("gatekeeper") {
    override suspend fun pump(context: GatekeeperContext) =
        withContext(ImperiumScope.MAIN.coroutineContext) {
            for (processor in processors) {
                val result =
                    try {
                        processor.process(context)
                    } catch (error: Exception) {
                        logger.error("Error while verifying player ${context.name}", error)
                        GatekeeperResult.Success
                    }
                if (result is GatekeeperResult.Failure) {
                    return@withContext result
                }
            }
            GatekeeperResult.Success
        }

    companion object {
        private val logger by LoggerDelegate()
    }
}
