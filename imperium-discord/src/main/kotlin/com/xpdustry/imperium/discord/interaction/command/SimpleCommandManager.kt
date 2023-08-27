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
package com.xpdustry.imperium.discord.interaction.command

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.discord.interaction.InteractionActor
import com.xpdustry.imperium.discord.interaction.Permission
import com.xpdustry.imperium.discord.interaction.Rank
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.javacord.api.entity.Attachment
import org.javacord.api.entity.channel.ServerChannel
import org.javacord.api.entity.user.User
import org.javacord.api.interaction.SlashCommandBuilder
import org.javacord.api.interaction.SlashCommandInteractionOption
import org.javacord.api.interaction.SlashCommandOption
import org.javacord.api.interaction.SlashCommandOptionBuilder
import org.javacord.api.interaction.SlashCommandOptionType
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspendBy
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
        registerHandler(ServerChannel::class, CHANNEL_TYPE_HANDLER)
        registerHandler(Attachment::class, ATTACHMENT_TYPE_HANDLER)
    }

    override fun onImperiumInit() {
        containers.forEach(::register0)
        compile()
        discord.api.addSlashCommandCreateListener { event ->
            ImperiumScope.MAIN.launch {
                val responder = event.slashCommandInteraction.respondLater(true).await()
                val node = tree.resolve(event.slashCommandInteraction.fullCommandName.split(" "))
                val command = node.edge!!
                val actor = InteractionActor.Slash(event)
                val arguments = try {
                    command.function.parameters.associateWith { parameter ->
                        if (parameter.index == 0) {
                            return@associateWith command.container
                        }

                        if (isSupportedActor(parameter.type.classifier!!)) {
                            return@associateWith actor
                        }

                        val argument = command.arguments.find { it.name == parameter.name!! }!!
                        val option = event.slashCommandInteraction.arguments.find { it.name == parameter.name!! }
                            ?.let { argument.handler.parse(it) }

                        if (option == null && !argument.optional) {
                            throw IllegalArgumentException("Missing required parameter: ${parameter.name}")
                        }

                        option
                    }
                } catch (e: Exception) {
                    logger.error("Error while parsing arguments for command ${node.fullName}", e)
                    responder.setContent(":warning: **An unexpected error occurred while parsing your command.**")
                        .update().await()
                    return@launch
                }

                try {
                    command.function.callSuspendBy(arguments)
                } catch (e: Exception) {
                    logger.error("Error while executing command ${node.fullName}", e)
                    responder.setContent(":warning: **An unexpected error occurred while executing your command.**")
                        .update().await()
                }
            }
        }
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

            if (!function.isSuspend) {
                throw IllegalArgumentException("$function must be suspend")
            }

            val arguments = mutableListOf<CommandEdge.Argument>()
            var wasOptional = false
            for (parameter in function.parameters) {
                // Skip "this"
                if (parameter.index == 0) {
                    continue
                }

                if (parameter.index == 1) {
                    if (!isSupportedActor(parameter.type.classifier!!)) {
                        throw IllegalArgumentException("First parameter is not a InteractionActor")
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

            tree.resolve(
                path = base + local.path.toList(),
                edge = CommandEdge(
                    container,
                    function,
                    function.findAnnotation<Permission>()?.rank ?: permission ?: Rank.EVERYONE,
                    arguments,
                ),
            )
        }
    }

    private fun compile() {
        val compiled = mutableSetOf<SlashCommandBuilder>()
        for (entry in tree.children) {
            val builder = SlashCommandBuilder()
                .setName(entry.key)
                .setDescription("No description.")

            for (child in entry.value.children.values) {
                compile(builder::addOption, child)
            }

            if (entry.value.edge != null) {
                compile(builder::addOption, entry.value.edge!!)
            }

            compiled.add(builder)
        }

        discord.api.bulkOverwriteServerApplicationCommands(discord.getMainServer(), compiled).join()
    }

    private fun compile(options: Consumer<SlashCommandOption>, node: CommandNode) {
        val builder = SlashCommandOptionBuilder()
            .setName(node.name)
            .setDescription("No description.")

        val type = when (node.type) {
            CommandNode.Type.COMMAND -> null
            CommandNode.Type.SUB_COMMAND_GROUP -> SlashCommandOptionType.SUB_COMMAND_GROUP
            CommandNode.Type.SUB_COMMAND -> SlashCommandOptionType.SUB_COMMAND
            CommandNode.Type.ROOT -> throw IllegalStateException("Root node cannot be compiled.")
        }

        if (type != null) {
            builder.setType(type)
        }

        for (child in node.children.values) {
            compile(builder::addOption, child)
        }

        if (node.edge != null) {
            compile(builder::addOption, node.edge!!)
        }

        options.accept(builder.build())
    }

    private fun compile(options: Consumer<SlashCommandOption>, edge: CommandEdge) {
        for (parameter in edge.arguments) {
            options.accept(
                SlashCommandOptionBuilder()
                    .setName(parameter.name)
                    .setDescription("No description.")
                    .setRequired(!parameter.optional)
                    .setType(parameter.handler.type)
                    .build(),
            )
        }
    }

    private fun <T : Any> registerHandler(klass: KClass<T>, handler: TypeHandler<T>) {
        handlers[klass] = handler
    }

    private fun isSupportedActor(classifier: KClassifier) =
        classifier == InteractionActor::class || classifier == InteractionActor.Slash::class

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

private data class CommandEdge(val container: Any, val function: KFunction<*>, val permission: Rank, val arguments: List<Argument>) {
    data class Argument(val name: String, val optional: Boolean, val handler: TypeHandler<*>)
}

private abstract class TypeHandler<T : Any>(val type: SlashCommandOptionType) {
    abstract fun parse(option: SlashCommandInteractionOption): T?
    open fun apply(builder: SlashCommandOptionBuilder, annotation: KAnnotatedElement) = Unit
}

private val STRING_TYPE_HANDLER = object : TypeHandler<String>(SlashCommandOptionType.STRING) {
    override fun parse(option: SlashCommandInteractionOption) = option.stringValue.getOrNull()
}

private val INT_TYPE_HANDLER = object : TypeHandler<Int>(SlashCommandOptionType.LONG) {
    override fun parse(option: SlashCommandInteractionOption) = option.longValue.getOrNull()?.toInt()
    override fun apply(builder: SlashCommandOptionBuilder, annotation: KAnnotatedElement) {
        builder.setLongMinValue(Int.MAX_VALUE.toLong())
        builder.setLongMaxValue(Int.MIN_VALUE.toLong())
    }
}

private val BOOLEAN_TYPE_HANDLER = object : TypeHandler<Boolean>(SlashCommandOptionType.BOOLEAN) {
    override fun parse(option: SlashCommandInteractionOption) = option.booleanValue.getOrNull()
}

private val USER_TYPE_HANDLER = object : TypeHandler<User>(SlashCommandOptionType.USER) {
    override fun parse(option: SlashCommandInteractionOption) = option.userValue.getOrNull()
}

private val CHANNEL_TYPE_HANDLER = object : TypeHandler<ServerChannel>(SlashCommandOptionType.CHANNEL) {
    override fun parse(option: SlashCommandInteractionOption) = option.channelValue.getOrNull()
}

private val ATTACHMENT_TYPE_HANDLER = object : TypeHandler<Attachment>(SlashCommandOptionType.ATTACHMENT) {
    override fun parse(option: SlashCommandInteractionOption) = option.attachmentValue.getOrNull()
}
