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
package com.xpdustry.imperium.discord.interaction.button

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.discord.interaction.InteractionActor
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.javacord.api.entity.message.MessageFlag
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

class SimpleButtonManager(private val discord: DiscordService) : ButtonManager, ImperiumApplication.Listener {
    private val handlers = mutableMapOf<String, ButtonHandler>()
    private val containers = mutableListOf<Any>()

    override fun onImperiumInit() {
        for (container in containers) {
            register0(container)
        }

        discord.getMainServer().addButtonClickListener { event ->
            ImperiumScope.MAIN.launch {
                val parts = event.buttonInteraction.customId.split(":", limit = 3)
                if (parts.size < 2) {
                    logger.error("Invalid button id ${event.buttonInteraction.customId}")
                    event.buttonInteraction.createImmediateResponder()
                        .setContent("This button is no longer valid")
                        .setFlags(MessageFlag.EPHEMERAL)
                        .respond()
                        .await()
                    return@launch
                }

                val handler = handlers[parts[0]]
                if (handler == null || handler.version != parts[1].toIntOrNull()) {
                    event.buttonInteraction.createImmediateResponder()
                        .setContent("This button is no longer valid")
                        .setFlags(MessageFlag.EPHEMERAL)
                        .respond()
                        .await()
                    return@launch
                }

                val updater = event.buttonInteraction.respondLater(true).await()
                val actor = InteractionActor.Button(event, parts.getOrNull(2))
                try {
                    handler.function.callSuspend(handler.container, actor)
                } catch (e: Exception) {
                    logger.error("Error while executing button ${event.buttonInteraction.customId}", e)
                    updater.setContent("An error occurred while executing this button")
                        .setFlags(MessageFlag.EPHEMERAL)
                        .update()
                        .await()
                }
            }
        }
    }

    override fun register(container: Any) {
        containers += container
    }

    private fun register0(container: Any) {
        for (function in container::class.memberFunctions) {
            val button = function.findAnnotation<InteractionButton>() ?: continue

            if (!button.name.all { it.isLetterOrDigit() || it == '-' }) {
                throw IllegalArgumentException("$function button name must be alphanumeric")
            }

            if (button.version < 1) {
                throw IllegalArgumentException("$function button version must be at least 1")
            }

            if (!function.isSuspend) {
                throw IllegalArgumentException("$function button must be suspend")
            }

            if (function.parameters.size != 2 || !isSupportedActor(function.parameters[1].type.classifier!!)) {
                throw IllegalArgumentException("$function button must have exactly one parameter of type InteractionActor")
            }

            if (button.name in handlers) {
                throw IllegalArgumentException("$function button ${button.name} is already registered")
            }

            function.isAccessible = true
            handlers[button.name] = ButtonHandler(button.version, container, function)
        }
    }

    private fun isSupportedActor(classifier: KClassifier) =
        classifier == InteractionActor::class || classifier == InteractionActor.Button::class

    companion object {
        private val logger by LoggerDelegate()
    }

    private data class ButtonHandler(val version: Int, val container: Any, val function: KFunction<*>)
}
