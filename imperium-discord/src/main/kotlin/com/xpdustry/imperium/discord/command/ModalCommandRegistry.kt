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
package com.xpdustry.imperium.discord.command

import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.discord.misc.addSuspendingEventListener
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.modals.ModalInteraction

class ModalCommandRegistry(private val discord: DiscordService) : AnnotationScanner, ImperiumApplication.Listener {

    private val handlers = mutableMapOf<String, ModalHandler>()

    override fun onImperiumInit() {
        discord.jda.addSuspendingEventListener<ModalInteractionEvent> { event ->
            val handler = handlers[event.modalId]
            if (handler == null) {
                event.deferReply(true).await().sendMessage("This modal is no longer valid").await()
                return@addSuspendingEventListener
            }

            @Suppress("UNCHECKED_CAST")
            val expected = handler.function.parameters[1].type.classifier as KClass<out Interaction>
            if (!isSupportedInteraction(event.interaction::class)) {
                logger.error(
                    "Unexpected modal type for {}, expected {}, got {}",
                    event.modalId,
                    expected,
                    event.interaction::class,
                )
                event
                    .deferReply(true)
                    .await()
                    .sendMessage("Unexpected modal type, please report this to a moderator")
                    .await()
                return@addSuspendingEventListener
            }

            try {
                handler.function.callSuspend(handler.container, event.interaction)
            } catch (e: Exception) {
                logger.error("Error while executing modal ${event.modalId}", e)
                try {
                    event.deferReply(true).await()
                } catch (_: Exception) {}
                event.hook.sendMessage("**:warning: An unexpected error occurred while handling this modal.**").await()
            }
        }
    }

    override fun scan(instance: Any) {
        for (function in instance::class.memberFunctions) {
            val command = function.findAnnotation<ModalCommand>() ?: continue

            if (!command.name.all { it.isLetterOrDigit() || it == '-' || it == ':' }) {
                throw IllegalArgumentException("$function modal name must be alphanumeric")
            }

            if (function.parameters.size != 2 || !isSupportedInteraction(function.parameters[1].type.classifier!!)) {
                throw IllegalArgumentException(
                    "$function button must have exactly one parameter of type ModalInteraction"
                )
            }

            if (command.name in handlers) {
                throw IllegalArgumentException("$function modal ${command.name} is already registered")
            }

            function.isAccessible = true
            handlers[command.name] = ModalHandler(instance, function)
        }
    }

    private fun isSupportedInteraction(classifier: KClassifier) =
        classifier.createType().jvmErasure.isSubclassOf(ModalInteraction::class)

    companion object {
        private val logger by LoggerDelegate()
    }

    private data class ModalHandler(val container: Any, val function: KFunction<*>)
}
