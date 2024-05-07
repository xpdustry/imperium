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
import com.xpdustry.imperium.discord.command.annotation.AlsoAllow
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.misc.addSuspendingEventListener
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

class ButtonCommandRegistry(private val discord: DiscordService) :
    AnnotationScanner, ImperiumApplication.Listener {
    private val handlers = mutableMapOf<String, ButtonHandler>()

    override fun onImperiumInit() {
        discord.jda.addSuspendingEventListener<ButtonInteractionEvent> { event ->
            val handler = handlers[event.componentId]
            if (handler == null) {
                event.deferReply(true).await().sendMessage("This button is no longer valid").await()
                return@addSuspendingEventListener
            }

            val sender = InteractionSender.Button(event)
            val updater = event.deferReply(handler.ephemeral).await()

            if (!handler.permission.test(sender)) {
                updater
                    .sendMessage(":warning: **You do not have permission to use this command.**")
                    .await()
                return@addSuspendingEventListener
            }

            try {
                handler.function.callSuspend(handler.container, sender)
            } catch (e: Exception) {
                logger.error("Error while executing button ${event.componentId}", e)
                updater.sendMessage("An error occurred while executing this button").await()
            }
        }
    }

    override fun scan(instance: Any) {
        for (function in instance::class.memberFunctions) {
            val button = function.findAnnotation<ButtonCommand>() ?: continue
            var permission = PermissionPredicate {
                discord.isAllowed(it.interaction.user, button.rank)
            }

            if (!button.name.all { it.isLetterOrDigit() || it == '-' || it == ':' }) {
                throw IllegalArgumentException("$function button name must be alphanumeric")
            }

            if (function.parameters.size != 2 ||
                !isSupportedActor(function.parameters[1].type.classifier!!)) {
                throw IllegalArgumentException(
                    "$function button must have exactly one parameter of type InteractionActor")
            }

            if (button.name in handlers) {
                throw IllegalArgumentException(
                    "$function button ${button.name} is already registered")
            }

            val allow = function.findAnnotation<AlsoAllow>()
            if (allow != null) {
                val previous = permission
                permission = PermissionPredicate {
                    previous.test(it) or discord.isAllowed(it.interaction.user, allow.permission)
                }
            }

            function.isAccessible = true
            handlers[button.name] =
                ButtonHandler(
                    instance,
                    permission,
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
        val permission: PermissionPredicate,
        val function: KFunction<*>,
        val ephemeral: Boolean,
    )
}
