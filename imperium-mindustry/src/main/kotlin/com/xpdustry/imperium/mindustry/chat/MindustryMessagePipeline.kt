// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.audience.Audience
import com.xpdustry.distributor.api.audience.PlayerAudience
import com.xpdustry.distributor.api.key.StandardKeys
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.mindustry.game.ClientDetector
import com.xpdustry.imperium.mindustry.processing.AbstractProcessorPipeline
import com.xpdustry.imperium.mindustry.processing.ProcessorPipeline
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class MindustryMessageContext
@JvmOverloads
constructor(
    val sender: Audience,
    val target: Audience,
    val message: String,
    val filter: Boolean = false,
    val kind: Kind = Kind.CHAT,
) {
    enum class Kind {
        CHAT,
        COMMAND,
    }
}

enum class MindustryMessageTemplate {
    CHAT,
    TEAM,
    SAY,
    WHISPER,
}

data class MindustryPlayerChatEvent(val player: PlayerAudience, val message: String)

interface MindustryMessagePipeline : ProcessorPipeline<MindustryMessageContext, String> {
    suspend fun broadcast(
        sender: Audience,
        target: Audience,
        message: String,
        template: MindustryMessageTemplate = MindustryMessageTemplate.CHAT,
    )
}

class SimpleMindustryMessagePipeline(
    private val formatter: MindustryAudienceFormatter,
    private val clients: ClientDetector,
) : MindustryMessagePipeline, AbstractProcessorPipeline<MindustryMessageContext, String>("mindustry-message") {
    private val logger = logger("mindustry-message-pipeline")

    override suspend fun pump(context: MindustryMessageContext): String {
        var result = context.message
        for (processor in processors) {
            result =
                try {
                    processor.process(context.copy(message = result))
                } catch (error: Throwable) {
                    logger.error(
                        "Error while processing message of {} to {}",
                        context.sender.metadata[StandardKeys.NAME] ?: "Unknown",
                        context.target.metadata[StandardKeys.NAME] ?: "Unknown",
                        error,
                    )
                    result
                }
            if (result.isBlank()) {
                break
            }
        }
        return result
    }

    override suspend fun broadcast(
        sender: Audience,
        target: Audience,
        message: String,
        template: MindustryMessageTemplate,
    ) = coroutineScope {
        target.audiences
            .map { audience ->
                async {
                    val processed = pump(MindustryMessageContext(sender, audience, message))
                    if (processed.isBlank()) {
                        return@async
                    }

                    val formatted = formatter.formatMessage(sender, processed, template)
                    if (formatted.isBlank()) {
                        return@async
                    }

                    audience.sendMessage(
                        Distributor.get().mindustryComponentDecoder.decode(formatted),
                        Distributor.get().mindustryComponentDecoder.decode(processed),
                        if (audience is PlayerAudience && clients.isFooClient(audience.player)) Audience.empty()
                        else sender,
                    )
                }
            }
            .awaitAll()
        Unit
    }
}
