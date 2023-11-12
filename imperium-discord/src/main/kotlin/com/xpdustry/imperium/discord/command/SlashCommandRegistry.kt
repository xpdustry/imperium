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

import com.xpdustry.imperium.common.account.Role
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.CommandRegistry
import com.xpdustry.imperium.common.command.annotation.Max
import com.xpdustry.imperium.common.command.annotation.Min
import com.xpdustry.imperium.common.command.validate
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.commands.PunishmentDuration
import com.xpdustry.imperium.discord.misc.getTranslatedTextOrNull
import com.xpdustry.imperium.discord.misc.toDiscordLocale
import com.xpdustry.imperium.discord.service.DiscordService
import java.time.Duration
import java.util.function.Consumer
import java.util.regex.Pattern
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.bson.types.ObjectId
import org.javacord.api.entity.Attachment
import org.javacord.api.entity.channel.ServerChannel
import org.javacord.api.entity.user.User
import org.javacord.api.interaction.DiscordLocale
import org.javacord.api.interaction.SlashCommandBuilder
import org.javacord.api.interaction.SlashCommandInteractionOption
import org.javacord.api.interaction.SlashCommandOption
import org.javacord.api.interaction.SlashCommandOptionBuilder
import org.javacord.api.interaction.SlashCommandOptionChoice
import org.javacord.api.interaction.SlashCommandOptionType

// TODO
//   - This is awful, rewrite it
//   - Arguments name are case insensitive, quite annoying
class SlashCommandRegistry(
    private val discord: DiscordService,
    private val config: ImperiumConfig
) : CommandRegistry, ImperiumApplication.Listener {
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
        registerHandler(Duration::class, DURATION_TYPE_HANDLER)
        registerHandler(ObjectId::class, OBJECT_ID_TYPE_HANDLER)
        registerHandler(PunishmentDuration::class, EnumTypeHandler(PunishmentDuration::class))
        registerHandler(MindustryGamemode::class, EnumTypeHandler(MindustryGamemode::class))
    }

    override fun onImperiumInit() {
        containers.forEach(::register0)
        compile()
        discord.api.addSlashCommandCreateListener { event ->
            ImperiumScope.MAIN.launch {
                val node = tree.resolve(event.slashCommandInteraction.fullCommandName.split(" "))
                val command = node.edge!!
                val responder =
                    event.slashCommandInteraction.respondLater(command.ephemeral).await()

                if (!discord.isAllowed(event.slashCommandInteraction.user, command.role)) {
                    responder
                        .setContent(":warning: **You do not have permission to use this command.**")
                        .update()
                        .await()
                    return@launch
                }

                val actor = InteractionSender.Slash(event)
                val arguments = mutableMapOf<KParameter, Any>()

                try {
                    for (parameter in command.function.parameters) {
                        if (parameter.index == 0) {
                            arguments[parameter] = command.container
                            continue
                        }

                        if (isSupportedActor(parameter.type.classifier!!)) {
                            arguments[parameter] = actor
                            continue
                        }

                        val argument = command.arguments.find { it.name == parameter.name!! }!!
                        val option =
                            try {
                                event.slashCommandInteraction.arguments
                                    .find { it.name == parameter.name!! }
                                    ?.let { argument.handler.parse(it) }
                            } catch (e: OptionParsingException) {
                                responder
                                    .setContent(
                                        ":warning: Failed to parse the **${argument.name}** argument: ${e.message}")
                                    .update()
                                    .await()
                                return@launch
                            }

                        if (option != null) {
                            arguments[parameter] = option
                            continue
                        }

                        if (!argument.optional) {
                            throw IllegalArgumentException(
                                "Missing required parameter: ${parameter.name}")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error while parsing arguments for command ${node.fullName}", e)
                    responder
                        .setContent(
                            ":warning: **An unexpected error occurred while parsing your command.**")
                        .update()
                        .await()
                    return@launch
                }

                try {
                    command.function.callSuspendBy(arguments)
                } catch (e: Exception) {
                    logger.error("Error while executing command ${node.fullName}", e)
                    responder
                        .setContent(
                            ":warning: **An unexpected error occurred while executing your command.**")
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
            val command = function.findAnnotation<Command>() ?: continue
            command.validate().getOrThrow()

            if (!function.isSuspend) {
                throw IllegalArgumentException("$function must be suspend")
            }

            val arguments = mutableListOf<CommandEdge.Argument<*>>()
            var wasOptional = false
            for (parameter in function.parameters) {
                // Skip "this"
                if (parameter.index == 0) {
                    continue
                }

                if (parameter.name!!.lowercase() != parameter.name) {
                    throw IllegalArgumentException(
                        "$function parameter names must be lowercase: ${parameter.name}")
                }

                if (parameter.index == 1) {
                    if (!isSupportedActor(parameter.type.classifier!!)) {
                        throw IllegalArgumentException(
                            "$function first parameter is not a InteractionActor")
                    }
                    continue
                }

                val optional = parameter.isOptional || parameter.type.isMarkedNullable
                if (wasOptional && !optional) {
                    throw IllegalArgumentException(
                        "$function optional parameters must be at the end of the parameter list.")
                }
                wasOptional = optional

                val classifier = parameter.type.classifier
                if (classifier !is KClass<*> || classifier !in handlers) {
                    throw IllegalArgumentException(
                        "$function has unsupported parameter type $classifier")
                }

                arguments += createCommandEdgeArgument(parameter.name!!, optional, classifier)
            }

            function.isAccessible = true
            tree.resolve(
                path = command.path.toList(),
                edge =
                    CommandEdge(
                        container,
                        function,
                        command.role,
                        arguments,
                        !function.hasAnnotation<NonEphemeral>(),
                    ),
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> createCommandEdgeArgument(
        name: String,
        optional: Boolean,
        klass: KClass<T>
    ): CommandEdge.Argument<T> {
        val handler =
            handlers[klass] as TypeHandler<T>?
                ?: throw IllegalArgumentException("Unsupported type $klass")
        return CommandEdge.Argument(name, optional, klass, handler)
    }

    private fun compile() {
        val compiled = mutableSetOf<SlashCommandBuilder>()
        for (entry in tree.children) {
            val builder = SlashCommandBuilder().setName(entry.key).setDescription(NO_DESCRIPTION)

            for (child in entry.value.children.values) {
                compile(builder::addOption, child)
            }

            if (entry.value.edge != null) {
                applyBuilderTranslations(
                    builder::setDescription,
                    builder::addDescriptionLocalization,
                    entry.value.edge!!)
                compile(builder::addOption, entry.value.edge!!)
            }

            compiled.add(builder)
        }

        discord.api.bulkOverwriteServerApplicationCommands(discord.getMainServer(), compiled).join()
    }

    private fun compile(options: Consumer<SlashCommandOption>, node: CommandNode) {
        val builder = SlashCommandOptionBuilder().setName(node.name).setDescription(NO_DESCRIPTION)

        val type =
            when (node.type) {
                CommandNode.Type.COMMAND -> null
                CommandNode.Type.SUB_COMMAND_GROUP -> SlashCommandOptionType.SUB_COMMAND_GROUP
                CommandNode.Type.SUB_COMMAND -> SlashCommandOptionType.SUB_COMMAND
                CommandNode.Type.ROOT ->
                    throw IllegalStateException("Root node cannot be compiled.")
            }

        if (type != null) {
            builder.setType(type)
        }

        for (child in node.children.values) {
            compile(builder::addOption, child)
        }

        if (node.edge != null) {
            applyBuilderTranslations(
                builder::setDescription, builder::addDescriptionLocalization, node.edge!!)
            compile(builder::addOption, node.edge!!)
        }

        options.accept(builder.build())
    }

    private fun compile(options: Consumer<SlashCommandOption>, edge: CommandEdge) {
        for (parameter in edge.arguments) {
            options.accept(compile(parameter, edge))
        }
    }

    private fun <T : Any> compile(argument: CommandEdge.Argument<T>, edge: CommandEdge) =
        SlashCommandOptionBuilder()
            .setName(argument.name)
            .setRequired(!argument.optional)
            .setType(argument.handler.type)
            .apply {
                argument.handler.apply(this, argument.annotations)
                applyBuilderTranslations(
                    this::setDescription, this::addDescriptionLocalization, edge, argument)
            }
            .build()

    private fun <T : Any> registerHandler(klass: KClass<T>, handler: TypeHandler<T>) {
        handlers[klass] = handler
    }

    private fun isSupportedActor(classifier: KClassifier) =
        classifier == InteractionSender::class || classifier == InteractionSender.Slash::class

    private fun applyBuilderTranslations(
        description: (String) -> Any,
        localizedDescription: (DiscordLocale, String) -> Any,
        edge: CommandEdge,
        argument: CommandEdge.Argument<*>? = null
    ) {
        val path = edge.function.findAnnotation<Command>()!!.path.joinToString(".")
        var key = "imperium.command.[$path]"
        key +=
            if (argument == null) {
                ".description"
            } else {
                ".argument.${argument.name}.description"
            }
        description(getTranslatedTextOrNull(key, config.language) ?: NO_DESCRIPTION)
        for (language in config.supportedLanguages) {
            getTranslatedTextOrNull(key, language)?.let {
                val locale = language.toDiscordLocale() ?: return@let
                localizedDescription(locale, it)
            }
        }
    }

    companion object {
        private val logger by LoggerDelegate()
        private const val NO_DESCRIPTION = "No description."
    }
}

private class CommandNode(val name: String, val parent: CommandNode?) {
    val children = mutableMapOf<String, CommandNode>()
    var edge: CommandEdge? = null
    var type: Type = if (parent == null) Type.ROOT else Type.COMMAND
    val fullName: String
        get() = if (parent == null) name else "${parent.fullName}.$name"

    fun resolve(path: List<String>, edge: CommandEdge? = null): CommandNode {
        if (path.isEmpty()) {
            if (edge != null) {
                if (this.edge != null) {
                    throw IllegalArgumentException("$fullName already has a registered command")
                }
                if (!(type == Type.SUB_COMMAND || (type == Type.COMMAND && children.isEmpty()))) {
                    throw IllegalArgumentException(
                        "$fullName is not valid for registering a command")
                }
                this.edge = edge
            }
            return this
        }

        if (path.size > type.ordinal) {
            throw IllegalArgumentException("Invalid path size for node type $type: $path")
        }

        val next =
            if (path[0] in children) {
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
        SUB_COMMAND,
        SUB_COMMAND_GROUP,
        COMMAND,
        ROOT
    }
}

private data class CommandEdge(
    val container: Any,
    val function: KFunction<*>,
    val role: Role,
    val arguments: List<Argument<*>>,
    val ephemeral: Boolean,
) {
    data class Argument<T : Any>(
        val name: String,
        val optional: Boolean,
        val annotations: KAnnotatedElement,
        val handler: TypeHandler<T>
    )
}

abstract class TypeHandler<T : Any>(val type: SlashCommandOptionType) {
    abstract fun parse(option: SlashCommandInteractionOption): T?

    open fun apply(builder: SlashCommandOptionBuilder, annotation: KAnnotatedElement) = Unit
}

private val STRING_TYPE_HANDLER =
    object : TypeHandler<String>(SlashCommandOptionType.STRING) {
        override fun parse(option: SlashCommandInteractionOption) = option.stringValue.getOrNull()

        override fun apply(builder: SlashCommandOptionBuilder, annotation: KAnnotatedElement) {
            annotation.findAnnotation<Min>()?.value?.let { builder.setMinLength(it) }
            annotation.findAnnotation<Max>()?.value?.let { builder.setMaxLength(it) }
        }
    }

private val INT_TYPE_HANDLER =
    object : TypeHandler<Int>(SlashCommandOptionType.LONG) {
        override fun parse(option: SlashCommandInteractionOption) =
            option.longValue.getOrNull()?.toInt()

        override fun apply(builder: SlashCommandOptionBuilder, annotation: KAnnotatedElement) {
            builder.setLongMinValue(
                annotation.findAnnotation<Min>()?.value ?: Int.MAX_VALUE.toLong())
            builder.setLongMaxValue(
                annotation.findAnnotation<Max>()?.value ?: Int.MIN_VALUE.toLong())
        }
    }

private val BOOLEAN_TYPE_HANDLER =
    object : TypeHandler<Boolean>(SlashCommandOptionType.BOOLEAN) {
        override fun parse(option: SlashCommandInteractionOption) = option.booleanValue.getOrNull()
    }

private val USER_TYPE_HANDLER =
    object : TypeHandler<User>(SlashCommandOptionType.USER) {
        override fun parse(option: SlashCommandInteractionOption) = option.userValue.getOrNull()
    }

private val CHANNEL_TYPE_HANDLER =
    object : TypeHandler<ServerChannel>(SlashCommandOptionType.CHANNEL) {
        override fun parse(option: SlashCommandInteractionOption) = option.channelValue.getOrNull()
    }

private val ATTACHMENT_TYPE_HANDLER =
    object : TypeHandler<Attachment>(SlashCommandOptionType.ATTACHMENT) {
        override fun parse(option: SlashCommandInteractionOption) =
            option.attachmentValue.getOrNull()
    }

// https://github.com/Incendo/cloud/blob/fda52448c20f5537c8f03aaf6a3b844119c20463/cloud-core/src/main/java/cloud/commandframework/arguments/standard/DurationArgument.java
private val DURATION_TYPE_HANDLER =
    object : TypeHandler<Duration>(SlashCommandOptionType.STRING) {
        private val DURATION_PATTERN = Pattern.compile("(([1-9][0-9]+|[1-9])[dhms])")

        override fun parse(option: SlashCommandInteractionOption): Duration? {
            val input = option.stringValue.getOrNull() ?: return null
            val matcher = DURATION_PATTERN.matcher(input)
            var duration = Duration.ZERO

            while (matcher.find()) {
                val group = matcher.group()
                val unit = group[group.length - 1].toString()
                val value = group.substring(0, group.length - 1)
                if (value.toIntOrNull() == null) {
                    throw OptionParsingException("$value is not a valid number")
                }
                duration =
                    when (unit) {
                        "d" -> duration.plusDays(value.toLong())
                        "h" -> duration.plusHours(value.toLong())
                        "m" -> duration.plusMinutes(value.toLong())
                        "s" -> duration.plusSeconds(value.toLong())
                        else -> throw OptionParsingException("Invalid time unit $unit")
                    }
            }

            if (duration.isZero) {
                throw OptionParsingException("The duration is zero")
            }
            return duration
        }
    }

private val OBJECT_ID_TYPE_HANDLER =
    object : TypeHandler<ObjectId>(SlashCommandOptionType.STRING) {
        override fun parse(option: SlashCommandInteractionOption): ObjectId? {
            val input = option.stringValue.getOrNull() ?: return null
            if (ObjectId.isValid(input)) {
                return ObjectId(input)
            }
            throw OptionParsingException("Invalid ObjectId")
        }
    }

class EnumTypeHandler<T : Enum<T>>(
    private val klass: KClass<T>,
    private val renderer: (T) -> String = { it.name.lowercase().replace("_", " ") }
) : TypeHandler<T>(SlashCommandOptionType.LONG) {
    override fun parse(option: SlashCommandInteractionOption): T? {
        val ordinal = option.longValue.getOrNull()?.toInt() ?: return null
        return klass.java.enumConstants[ordinal]
    }

    override fun apply(builder: SlashCommandOptionBuilder, annotation: KAnnotatedElement) {
        klass.java.enumConstants.forEach {
            val choice = it as T
            builder.addChoice(
                SlashCommandOptionChoice.create(renderer(choice), choice.ordinal.toLong()))
        }
    }
}

class OptionParsingException(message: String) : Exception(message)
