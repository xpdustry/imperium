// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.dependency.Inject
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

@Inject
class SimpleGatekeeperPipeline constructor() :
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
