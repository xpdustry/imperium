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
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.discord.service.DiscordService
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.core.`object`.command.ApplicationCommandInteractionOptionValue
import discord4j.core.`object`.command.ApplicationCommandOption
import discord4j.core.`object`.entity.Attachment
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.discordjson.json.ApplicationCommandRequest
import discord4j.discordjson.json.ImmutableApplicationCommandOptionData
import reactor.core.publisher.Mono
import java.util.function.Consumer
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

// TODO
//  - Implement permission validation
class SimpleCommandManager(private val discord: DiscordService) : CommandManager, ImperiumApplication.Listener {
    private val containers = mutableListOf<Any>()
    private val handlers = mutableMapOf<KClass<*>, TypeHandler<*>>()
    private val tree = CommandNode("root", parent = null)

    init {
        registerHandler(String::class, STRING_TYPE_HANDLER)
        registerHandler(Int::class, INT_TYPE_HANDLER)
        registerHandler(Boolean::class, BOOLEAN_TYPE_HANDLER)
        registerHandler(User::class, USER_TYPE_HANDLER)
        registerHandler(Channel::class, CHANNEL_TYPE_HANDLER)
        registerHandler(Attachment::class, ATTACHMENT_TYPE_HANDLER)
    }

    override fun onImperiumInit() {
        containers.forEach(::register0)
        compile()
        discord.gateway
            .on(ChatInputInteractionEvent::class.java)
            .flatMap { it.deferReply().withEphemeral(true).thenReturn(it) }
            .flatMap { event ->
                var node = tree.resolve(listOf(event.commandName))
                var options = event.options
                while (options.size == 1 &&
                    (
                        options[0].type == ApplicationCommandOption.Type.SUB_COMMAND ||
                            options[0].type == ApplicationCommandOption.Type.SUB_COMMAND_GROUP
                        )
                ) {
                    node = node.resolve(listOf(options[0].name))
                    options = options[0].options
                }

                val command = node.edge!!
                val actor = CommandActor(event)
                Mono.fromCallable {
                    command.function.parameters.associateWith { parameter ->
                        if (parameter.index == 0) {
                            return@associateWith command.container
                        }

                        if (parameter.type.classifier == CommandActor::class) {
                            return@associateWith actor
                        }

                        val argument = command.arguments.find { it.name == parameter.name!! }!!
                        val option = options.find { it.name == parameter.name!! }
                        if (option == null || option.value.isEmpty) {
                            if (argument.optional) {
                                return@associateWith null
                            }
                            throw IllegalArgumentException("Missing required parameter: ${parameter.name}")
                        }

                        argument.handler.parse(option.value.get())
                    }
                }
                    .onErrorResume {
                        logger.error("Error while parsing arguments for command ${node.fullName}", it)
                        actor.reply(":warning: **An unexpected error occurred while parsing your command.**")
                            .then(Mono.empty())
                    }
                    .flatMap { arguments ->
                        Mono.defer { command.function.callBy(arguments).then() }
                            .onErrorResume {
                                logger.error("Error while executing command ${node.fullName}", it)
                                actor.reply(":warning: **An unexpected error occurred while executing your command.**")
                                    .then()
                            }
                    }
            }
            .subscribe()
    }

    override fun register(container: Any) {
        containers.add(container)
    }

    private fun register0(container: Any) {
        val global = container::class.findAnnotation<Command>()?.apply(Command::validate)
        val base = global?.path?.toList() ?: emptyList()
        val permission = container::class.findAnnotation<Permission>()?.rank

        for (function in container::class.memberFunctions) {
            val local = function.findAnnotation<Command>()?.apply(Command::validate) ?: continue

            if (function.returnType.classifier != Mono::class) {
                throw IllegalArgumentException("$function must return a Mono")
            }

            val arguments = mutableListOf<CommandEdge.Argument>()
            var wasOptional = false
            for (parameter in function.parameters) {
                // Skip "this"
                if (parameter.index == 0) {
                    continue
                }

                if (parameter.index == 1) {
                    if (parameter.type.classifier != CommandActor::class) {
                        throw IllegalArgumentException("First parameter must be of type CommandActor")
                    }
                    continue
                }

                val optional = parameter.isOptional || parameter.type.isMarkedNullable
                if (wasOptional && !optional) {
                    throw IllegalArgumentException("Optional parameters must be at the end of the parameter list.")
                }
                wasOptional = optional

                val classifier = parameter.type.classifier
                if (classifier !is KClass<*> || classifier !in handlers) {
                    throw IllegalArgumentException("Unsupported parameter type $classifier")
                }

                arguments += CommandEdge.Argument(parameter.name!!, optional, handlers[classifier]!!)
            }

            @Suppress("UNCHECKED_CAST")
            tree.resolve(
                path = base + local.path.toList(),
                edge = CommandEdge(
                    container,
                    function as KFunction<Mono<*>>,
                    function.findAnnotation<Permission>()?.rank ?: permission ?: Rank.EVERYONE,
                    arguments,
                ),
            )
        }
    }

    private fun compile() {
        val compiled = mutableListOf<ApplicationCommandRequest>()
        for (entry in tree.children) {
            val builder = ApplicationCommandRequest.builder()
                .name(entry.key)
                .description("No description.")

            for (child in entry.value.children.values) {
                compile(builder::addOption, child)
            }

            if (entry.value.edge != null) {
                compile(builder::addOption, entry.value.edge!!)
            }

            compiled.add(builder.build())
        }

        discord.gateway.restClient.applicationService.bulkOverwriteGuildApplicationCommand(
            discord.gateway.restClient.applicationId.block()!!,
            discord.getMainGuild().block()!!.id.asLong(),
            compiled,
        )
            .collectList()
            .block()
    }

    private fun compile(options: Consumer<ApplicationCommandOptionData>, node: CommandNode) {
        val builder = ApplicationCommandOptionData.builder()
            .name(node.name)
            .description("No description.")
            .type(
                when (node.type) {
                    CommandNode.Type.COMMAND -> 0
                    CommandNode.Type.SUB_COMMAND_GROUP -> ApplicationCommandOption.Type.SUB_COMMAND_GROUP.value
                    CommandNode.Type.SUB_COMMAND -> ApplicationCommandOption.Type.SUB_COMMAND.value
                    CommandNode.Type.ROOT -> throw IllegalStateException("Root node cannot be compiled.")
                },
            )

        for (child in node.children.values) {
            compile(builder::addOption, child)
        }

        if (node.edge != null) {
            compile(builder::addOption, node.edge!!)
        }

        options.accept(builder.build())
    }

    private fun compile(options: Consumer<ApplicationCommandOptionData>, edge: CommandEdge) {
        for (parameter in edge.arguments) {
            options.accept(
                ApplicationCommandOptionData.builder()
                    .name(parameter.name)
                    .description("No description.")
                    .required(!parameter.optional)
                    .type(parameter.handler.type.value)
                    .build(),
            )
        }
    }

    private fun <T : Any> registerHandler(klass: KClass<T>, handler: TypeHandler<T>) {
        handlers[klass] = handler
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}

private class CommandNode(val name: String, val parent: CommandNode?) {
    val children = mutableMapOf<String, CommandNode>()
    var edge: CommandEdge? = null
    var type: Type = if (parent == null) Type.ROOT else Type.COMMAND
    val fullName: String get() = if (parent == null) name else "${parent.fullName}.$name"

    fun resolve(path: List<String>, edge: CommandEdge? = null): CommandNode {
        if (path.isEmpty()) {
            if (edge != null) {
                if (this.edge != null) {
                    throw IllegalArgumentException("$fullName already has a registered command")
                }
                if (!(type == Type.SUB_COMMAND || (type == Type.COMMAND && children.isEmpty()))) {
                    throw IllegalArgumentException("$fullName is not valid for registering a command")
                }
                this.edge = edge
            }
            return this
        }

        if (path.size > type.ordinal) {
            throw IllegalArgumentException("Invalid path size for node type $type: $path")
        }

        val next = if (path[0] in children) {
            children[path[0]]!!
        } else if (edge != null) {
            children.computeIfAbsent(path[0]) { CommandNode(it, this) }
        } else {
            throw IllegalArgumentException("Element does not exist in node $this: $path")
        }

        if (edge != null && type != Type.ROOT) {
            next.type = if (path.size == 2) Type.SUB_COMMAND_GROUP else Type.SUB_COMMAND
        }

        return next.resolve(path.subList(1, path.size), edge)
    }

    enum class Type {
        SUB_COMMAND, SUB_COMMAND_GROUP, COMMAND, ROOT
    }
}

private data class CommandEdge(val container: Any, val function: KFunction<Mono<*>>, val permission: Rank, val arguments: List<Argument>) {
    data class Argument(val name: String, val optional: Boolean, val handler: TypeHandler<*>)
}

private abstract class TypeHandler<T : Any>(val type: ApplicationCommandOption.Type) {
    abstract fun parse(option: ApplicationCommandInteractionOptionValue): T
    open fun apply(builder: ImmutableApplicationCommandOptionData.Builder, annotation: KAnnotatedElement) = Unit
}

private val STRING_TYPE_HANDLER = object : TypeHandler<String>(ApplicationCommandOption.Type.STRING) {
    override fun parse(option: ApplicationCommandInteractionOptionValue): String = option.asString()
}

private val INT_TYPE_HANDLER = object : TypeHandler<Int>(ApplicationCommandOption.Type.INTEGER) {
    override fun parse(option: ApplicationCommandInteractionOptionValue): Int = option.asLong().toInt()
    override fun apply(builder: ImmutableApplicationCommandOptionData.Builder, annotation: KAnnotatedElement) {
        builder.maxValue(Int.MAX_VALUE.toDouble())
        builder.minValue(Int.MIN_VALUE.toDouble())
    }
}

private val BOOLEAN_TYPE_HANDLER = object : TypeHandler<Boolean>(ApplicationCommandOption.Type.BOOLEAN) {
    override fun parse(option: ApplicationCommandInteractionOptionValue): Boolean = option.asBoolean()
}

private val USER_TYPE_HANDLER = object : TypeHandler<User>(ApplicationCommandOption.Type.USER) {
    override fun parse(option: ApplicationCommandInteractionOptionValue): User = option.asUser().block()!!
}

private val CHANNEL_TYPE_HANDLER = object : TypeHandler<Channel>(ApplicationCommandOption.Type.CHANNEL) {
    override fun parse(option: ApplicationCommandInteractionOptionValue): Channel = option.asChannel().block()!!
}

private val ATTACHMENT_TYPE_HANDLER = object : TypeHandler<Attachment>(ApplicationCommandOption.Type.ATTACHMENT) {
    override fun parse(option: ApplicationCommandInteractionOptionValue): Attachment = option.asAttachment()
}
