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
package com.xpdustry.imperium.discord.command

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.CommandRegistry
import com.xpdustry.imperium.common.command.Permission
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.javacord.api.entity.message.MessageFlag
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

class ButtonCommandRegistry(private val discord: DiscordService) : CommandRegistry, ImperiumApplication.Listener {
    private val handlers = mutableMapOf<String, ButtonHandler>()
    private val containers = mutableListOf<Any>()

    override fun onImperiumInit() {
        for (container in containers) {
            register0(container)
        }

        discord.getMainServer().addButtonClickListener { event ->
            ImperiumScope.MAIN.launch {
                val handler = handlers[event.buttonInteraction.customId]
                if (handler == null) {
                    event.buttonInteraction.createImmediateResponder()
                        .setContent("This button is no longer valid")
                        .setFlags(MessageFlag.EPHEMERAL)
                        .respond()
                        .await()
                    return@launch
                }

                val updater = event.buttonInteraction.respondLater(handler.ephemeral).await()

                if (!discord.isAllowed(event.buttonInteraction.user, handler.permission)) {
                    updater.setContent(":warning: **You do not have permission to use this command.**")
                        .update().await()
                    return@launch
                }

                val actor = InteractionSender.Button(event)
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

    override fun parse(container: Any) {
        containers += container
    }

    private fun register0(container: Any) {
        for (function in container::class.memberFunctions) {
            val button = function.findAnnotation<ButtonCommand>() ?: continue

            if (!button.name.all { it.isLetterOrDigit() || it == '-' || it == ':' }) {
                throw IllegalArgumentException("$function button name must be alphanumeric")
            }

            if (function.parameters.size != 2 || !isSupportedActor(function.parameters[1].type.classifier!!)) {
                throw IllegalArgumentException("$function button must have exactly one parameter of type InteractionActor")
            }

            if (button.name in handlers) {
                throw IllegalArgumentException("$function button ${button.name} is already registered")
            }

            function.isAccessible = true
            handlers[button.name] = ButtonHandler(
                container,
                button.permission,
                function,
                !function.hasAnnotation<NonEphemeral>(),
            )
        }
    }

    private fun isSupportedActor(classifier: KClassifier) =
        classifier == InteractionSender::class || classifier == InteractionSender.Button::class

    companion object {
        private val logger by LoggerDelegate()
    }

    private data class ButtonHandler(
        val container: Any,
        val permission: Permission,
        val function: KFunction<*>,
        val ephemeral: Boolean,
    )
}
