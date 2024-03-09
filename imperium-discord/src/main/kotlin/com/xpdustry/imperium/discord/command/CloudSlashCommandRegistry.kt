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

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.CommandRegistry
import com.xpdustry.imperium.common.command.name
import com.xpdustry.imperium.common.command.validate
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.commands.PunishmentDuration
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import io.leangen.geantyref.TypeToken
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.annotations.descriptor.CommandDescriptor
import org.incendo.cloud.annotations.descriptor.ImmutableCommandDescriptor
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.discord.jda5.JDA5CommandManager
import org.incendo.cloud.discord.slash.CommandScope
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.key.CloudKey
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.meta.CommandMeta
import org.incendo.cloud.parser.ParserDescriptor
import org.incendo.cloud.parser.ParserParameter
import org.incendo.cloud.parser.ParserParameters
import org.incendo.cloud.parser.standard.EnumParser

class CloudSlashCommandRegistry(private val discord: DiscordService) :
    CommandRegistry, ImperiumApplication.Listener {

    private val manager =
        JDA5CommandManager(ExecutionCoordinator.simpleCoordinator()) {
                InteractionSender.Slash(it.interactionEvent() as SlashCommandInteractionEvent)
            }
            .apply {
                parserRegistry()
                    .registerParser(enumParser<PunishmentDuration>())
                    .registerParser(enumParser<Rank>())
                    .registerParser(enumParser<MindustryGamemode>())

                registerCommandPostProcessor {
                    runBlocking {
                        it.commandContext()
                            .sender()
                            .interaction
                            .deferReply(it.command().commandMeta()[EPHEMERAL_CLOUD_KEY]!!)
                            .await()
                    }
                }
            }

    private val parser =
        AnnotationParser(manager, InteractionSender.Slash::class.java) {
                CommandMeta.builder()
                    .with(EPHEMERAL_CLOUD_KEY, it.get(EPHEMERAL_PARSER_KEY, true))
                    .build()
            }
            .apply {
                installCoroutineSupport(
                    scope = ImperiumScope.MAIN,
                    context = ImperiumScope.MAIN.coroutineContext,
                    onlyForSuspending = true)

                commandExtractor {
                    imperiumExtractCommands(it, this, InteractionSender.Slash::class)
                }
                registerAnnotationMapper(NonEphemeral::class.java) {
                    ParserParameters.single(EPHEMERAL_PARSER_KEY, false)
                }
            }

    override fun parse(container: Any) {
        parser.parse(container)
    }

    override fun onImperiumInit() {
        runBlocking {
            discord.jda
                .updateCommands()
                .addCommands(
                    manager
                        .commandFactory()
                        .createCommands(CommandScope.guilds(discord.getMainServer().idLong)))
                .await()
        }
        discord.jda.addEventListener(manager.createListener())
    }
}

private val EPHEMERAL_PARSER_KEY =
    ParserParameter("imperium:ephemeral", TypeToken.get(Boolean::class.javaObjectType))
private val EPHEMERAL_CLOUD_KEY =
    CloudKey.of("imperium:ephemeral", TypeToken.get(Boolean::class.javaObjectType))

private fun <S : Any> imperiumExtractCommands(
    instance: Any,
    parser: AnnotationParser<S>,
    sender: KClass<S>
): Collection<CommandDescriptor> {
    val descriptors = mutableListOf<CommandDescriptor>()

    for (function in instance::class.memberFunctions) {
        val command = function.findAnnotation<Command>() ?: continue
        command.validate().getOrThrow()

        if (!function.isSuspend) {
            throw IllegalArgumentException("$function must be suspend")
        }

        val syntax = buildString {
            append(command.path.joinToString(" "))

            for (parameter in function.parameters) {
                // Skip "this"
                if (parameter.index == 0) {
                    continue
                }

                if (parameter.name!!.lowercase() != parameter.name) {
                    throw IllegalArgumentException(
                        "$function parameter names must be lowercase: ${parameter.name}")
                }

                if (parameter.type.classifier == CommandContext::class ||
                    parameter.type.classifier == sender) {
                    continue
                }

                append(" ")
                if (parameter.isOptional || parameter.type.isMarkedNullable) {
                    append("[").append(parameter.name).append("]")
                } else {
                    append("<").append(parameter.name).append(">")
                }
            }
        }

        val processed =
            parser.syntaxParser().parseSyntax(function.javaMethod, parser.processString(syntax))
        descriptors +=
            ImmutableCommandDescriptor.builder()
                .method(function.javaMethod!!)
                .syntax(processed)
                .commandToken(parser.processString(command.name))
                .requiredSender(sender.java)
                .build()
    }

    return descriptors
}

private inline fun <reified E : Enum<E>> enumParser():
    ParserDescriptor<InteractionSender.Slash, E> = EnumParser.enumParser(E::class.java)
