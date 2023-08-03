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
import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.command.ApplicationCommandOption.Type
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.discordjson.json.ApplicationCommandRequest
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

// TODO
//  - This manager is not clean at all, but do I really want to spend more time on this? Probably not.
//  - Implement permission validation
//  - Lack of validation before command execution
//  - Check return type of command functions
class SimpleCommandManager(private val discord: DiscordService) : CommandManager, ImperiumApplication.Listener {

    private val containers = mutableListOf<Any>()
    private val tree = CommandNode("root", null)

    override fun onImperiumInit() {
        containers.forEach(::register0)
        compile()
        discord.gateway.on(ChatInputInteractionEvent::class.java).subscribe { event ->
            var node = tree.resolveOrThrow(event.commandName)
            var options = emptyList<ApplicationCommandInteractionOption>()
            for (i in 0 until event.options.size) {
                val option = event.options[i]
                if (option.type == Type.SUB_COMMAND || option.type == Type.SUB_COMMAND_GROUP) {
                    node = node.resolveOrThrow(option.name)
                    continue
                }
                options = event.options.subList(i, event.options.size)
                break
            }

            val actor = CommandActor(event)
            val parameters = mutableListOf<Any?>()
            for (parameter in node.command!!.parameters) {
                if (parameter.index == 0) {
                    parameters += node.container!!
                    continue
                }

                if (parameter.type.classifier == CommandActor::class) {
                    parameters += actor
                    continue
                }

                val value = options.find { it.name == parameter.name!! }?.value?.getOrNull()
                if (value == null) {
                    if (parameter.isOptional || parameter.type.isMarkedNullable) {
                        parameters += null
                        continue
                    }
                    throw IllegalArgumentException("Missing required parameter: ${parameter.name}")
                }

                parameters += when (findDiscordType(parameter.type.classifier as KClass<*>)!!) {
                    Type.SUB_COMMAND, Type.SUB_COMMAND_GROUP, Type.UNKNOWN, Type.MENTIONABLE -> throw IllegalArgumentException("Invalid parameter type: ${parameter.type}")
                    Type.BOOLEAN -> value.asBoolean()
                    Type.INTEGER -> value.asLong().toInt()
                    Type.NUMBER -> value.asDouble()
                    Type.STRING -> value.asString()
                    Type.USER -> value.asUser().block()!!
                    Type.CHANNEL -> value.asChannel().block()!!
                    Type.ROLE -> value.asRole().block()!!
                    Type.ATTACHMENT -> value.asAttachment()
                }
            }

            node.command!!.call(*parameters.toTypedArray())
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
            val node = tree.resolveAndCreate(base + local.path.toList())
            node.command = function.apply { isAccessible = true }
            node.permission = function.findAnnotation<Permission>()?.rank ?: permission ?: Permission.Rank.EVERYONE
            node.container = container

            var wasRequired = true
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

                if (wasRequired && (parameter.isOptional || parameter.type.isMarkedNullable)) {
                    throw IllegalArgumentException("Optional parameters must be at the end of the parameter list.")
                }
                wasRequired = !(parameter.isOptional || parameter.type.isMarkedNullable)
                val classifier = parameter.type.classifier
                if (classifier !is KClass<*> || findDiscordType(classifier) == null) {
                    throw IllegalArgumentException("Unsupported parameter type $classifier")
                }
            }
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

            if (entry.value.command != null) {
                compile(builder::addOption, entry.value.command!!)
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
                    CommandNode.Type.COMMAND -> Type.SUB_COMMAND.value
                    CommandNode.Type.SUB_COMMAND_GROUP -> Type.SUB_COMMAND_GROUP.value
                    CommandNode.Type.SUB_COMMAND -> Type.SUB_COMMAND.value
                    CommandNode.Type.ROOT -> throw IllegalStateException("Root node cannot be compiled.")
                },
            )

        for (child in node.children.values) {
            compile(builder::addOption, child)
        }

        if (node.command != null) {
            compile(builder::addOption, node.command!!)
        }

        options.accept(builder.build())
    }

    private fun compile(options: Consumer<ApplicationCommandOptionData>, function: KFunction<*>) {
        for (parameter in function.parameters) {
            if (parameter.type.classifier == CommandActor::class || parameter.index == 0) {
                continue
            }

            val option = ApplicationCommandOptionData.builder()
                .name(parameter.name!!)
                .description("No description.")
                .required(!parameter.isOptional)

            val type = findDiscordType(parameter.type.classifier as KClass<*>)
                ?: throw IllegalArgumentException("Unsupported argument type " + parameter.type.classifier)

            options.accept(option.type(type.value).build())
        }
    }

    private fun findDiscordType(klass: KClass<*>): Type? = when (klass) {
        String::class -> Type.STRING
        Int::class -> Type.INTEGER
        User::class -> Type.USER
        Channel::class -> Type.CHANNEL
        else -> null
    }

    private class CommandNode(val name: String, val parent: CommandNode?) {
        var permission = Permission.Rank.EVERYONE
        var command: KFunction<*>? = null
        var type: Type = if (parent == null) Type.ROOT else Type.COMMAND
        var container: Any? = null
        val children = mutableMapOf<String, CommandNode>()

        fun resolveAndCreate(path: List<String>): CommandNode {
            if (path.isEmpty()) {
                throw IllegalArgumentException("Path cannot be empty")
            }
            var node = this
            for (name in path) {
                var previous = node
                while (previous.type != Type.ROOT) {
                    previous.type = Type.entries.getOrNull(previous.type.ordinal + 1)
                        ?: throw IllegalStateException("Reached the maximum depth of 3: $this, $path")
                    previous = previous.parent!!
                }
                node = node.children.computeIfAbsent(name) { CommandNode(it, node) }
                if (node.command != null) {
                    throw IllegalStateException("Cannot add subcommand to a command: $this, $path")
                }
            }
            return node
        }

        fun resolveOrThrow(vararg path: String): CommandNode {
            var node: CommandNode = this
            for (name in path) {
                node = node.children[name]!!
            }
            return node
        }

        enum class Type {
            ROOT, COMMAND, SUB_COMMAND, SUB_COMMAND_GROUP,
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
