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
import com.xpdustry.imperium.discord.misc.addSuspendingEventListener
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.misc.getTranslatedTextOrNull
import com.xpdustry.imperium.discord.misc.toDiscordLocale
import com.xpdustry.imperium.discord.service.DiscordService
import java.time.Duration
import java.util.regex.Pattern
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
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User as DiscordUser
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData

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
        registerHandler(Long::class, LONG_TYPE_HANDLER)
        registerHandler(Boolean::class, BOOLEAN_TYPE_HANDLER)
        registerHandler(DiscordUser::class, DISCORD_USER_TYPE_HANDLER)
        registerHandler(GuildChannel::class, CHANNEL_TYPE_HANDLER)
        registerHandler(Message.Attachment::class, ATTACHMENT_TYPE_HANDLER)
        registerHandler(Duration::class, DURATION_TYPE_HANDLER)
        registerHandler(PunishmentDuration::class, EnumTypeHandler(PunishmentDuration::class))
        registerHandler(MindustryGamemode::class, EnumTypeHandler(MindustryGamemode::class))
        registerHandler(Rank::class, EnumTypeHandler(Rank::class))
    }

    override fun onImperiumInit() {
        containers.forEach(::register0)
        compile()
        discord.jda.addSuspendingEventListener<SlashCommandInteractionEvent> { event ->
            val node = tree.resolve(event.interaction.fullCommandName.split(" "))
            val command = node.edge!!
            val responder = event.deferReply(command.ephemeral).await()

            if (!discord.isAllowed(event.user, command.rank)) {
                responder
                    .sendMessage(":warning: **You do not have permission to use this command.**")
                    .await()
                return@addSuspendingEventListener
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
                            event.getOption(parameter.name!!)?.let { argument.handler.parse(it) }
                        } catch (e: OptionParsingException) {
                            responder
                                .sendMessage(
                                    ":warning: Failed to parse the **${argument.name}** argument: ${e.message}")
                                .await()
                            return@addSuspendingEventListener
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
                LOGGER.error("Error while parsing arguments for command ${node.fullName}", e)
                responder
                    .sendMessage(
                        ":warning: **An unexpected error occurred while parsing your command.**")
                    .await()
                return@addSuspendingEventListener
            }

            try {
                command.function.callSuspendBy(arguments)
            } catch (e: Exception) {
                LOGGER.error("Error while executing command ${node.fullName}", e)
                responder
                    .sendMessage(
                        ":warning: **An unexpected error occurred while executing your command.**")
                    .await()
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
                        command.rank,
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
        val compiled = mutableSetOf<SlashCommandData>()
        for (entry in tree.children) {
            val wrapper = SlashCommandNode.Slash(entry.key)
            compile(wrapper, entry.value)
            wrapper.applyBuilderTranslations()
            compiled.add(wrapper.delegate)
        }

        runBlocking { discord.getMainServer().updateCommands().addCommands(compiled).await() }
    }

    private fun compile(parent: SlashCommandNode, node: CommandNode) {
        when (node.height) {
            0 -> throw IllegalStateException("Root node cannot be compiled.")
            1 -> {
                for (argument in node.edge?.arguments ?: emptyList()) {
                    val wrapper =
                        SlashCommandNode.Option(argument.name, parent.path, argument.handler.type)
                    wrapper.delegate.isRequired = !argument.optional
                    argument.handler.apply(wrapper.delegate, argument.annotations)
                    wrapper.applyBuilderTranslations()
                    parent.addOptions(wrapper.delegate)
                }
            }
            else -> {
                for (child in node.children.values) {
                    when (child.height) {
                        1 -> {
                            val wrapper =
                                SlashCommandNode.Subcommand(child.name, parent.path + child.name)
                            compile(wrapper, child)
                            wrapper.applyBuilderTranslations()
                            parent.addSubcommands(wrapper.delegate)
                        }
                        2 -> {
                            val wrapper =
                                SlashCommandNode.SubcommandGroup(
                                    child.name, parent.path + child.name)
                            compile(wrapper, child)
                            parent.addSubcommandGroups(wrapper.delegate)
                        }
                        else -> error("How ?")
                    }
                }
            }
        }
    }

    private fun <T : Any> registerHandler(klass: KClass<T>, handler: TypeHandler<T>) {
        handlers[klass] = handler
    }

    private fun isSupportedActor(classifier: KClassifier) =
        classifier == InteractionSender::class || classifier == InteractionSender.Slash::class

    private fun SlashCommandNode.applyBuilderTranslations() {
        var key = "imperium.command.[${path.joinToString(".")}]"
        key +=
            if (this is SlashCommandNode.Option) {
                ".argument.${delegate.name}.description"
            } else {
                ".description"
            }
        setDescription(getTranslatedTextOrNull(key, config.language) ?: NO_DESCRIPTION)
        for (language in config.supportedLanguages) {
            getTranslatedTextOrNull(key, language)?.let { description ->
                val locale = language.toDiscordLocale() ?: return@let
                setLocalizedDescription(locale, description)
            }
        }
    }

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}

private const val NO_DESCRIPTION = "No description."

private data class CommandNode(val name: String, val parent: CommandNode?) {
    val children = mutableMapOf<String, CommandNode>()
    var edge: CommandEdge? = null
    val fullName: String
        get() = if (parent == null) name else "${parent.fullName}.$name"

    val depth: Int
        get() {
            var count = 0
            var node: CommandNode? = this.parent
            while (node != null) {
                count++
                node = node.parent
            }
            return count
        }

    val height: Int
        get() = 1 + (children.maxOfOrNull { it.value.height } ?: 0)

    fun resolve(path: List<String>, edge: CommandEdge? = null): CommandNode {
        if (depth > 3) {
            throw IllegalArgumentException("The command tree is too deep: $path")
        }

        if (path.isEmpty()) {
            if (edge != null) {
                if (this.edge != null) {
                    throw IllegalArgumentException("$fullName already has a registered command")
                }
                this.edge = edge
            }
            return this
        }

        val next =
            if (path[0] in children) {
                children[path[0]]!!
            } else if (edge != null) {
                children.computeIfAbsent(path[0]) { CommandNode(it, this) }
            } else {
                throw IllegalArgumentException("Element does not exist in node $this: $path")
            }

        return next.resolve(path.subList(1, path.size), edge)
    }
}

private data class CommandEdge(
    val container: Any,
    val function: KFunction<*>,
    val rank: Rank,
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

abstract class TypeHandler<T : Any>(val type: OptionType) {
    abstract fun parse(option: OptionMapping): T?

    open fun apply(builder: OptionData, annotation: KAnnotatedElement) = Unit
}

private val STRING_TYPE_HANDLER =
    object : TypeHandler<String>(OptionType.STRING) {
        override fun parse(option: OptionMapping) = option.asString

        override fun apply(builder: OptionData, annotation: KAnnotatedElement) {
            annotation.findAnnotation<Min>()?.value?.let { builder.setMinLength(it.toInt()) }
            annotation.findAnnotation<Max>()?.value?.let { builder.setMaxLength(it.toInt()) }
        }
    }

private val INT_TYPE_HANDLER =
    object : TypeHandler<Int>(OptionType.INTEGER) {
        override fun parse(option: OptionMapping) = option.asInt

        override fun apply(builder: OptionData, annotation: KAnnotatedElement) {
            builder.setMinValue(annotation.findAnnotation<Min>()?.value ?: Int.MIN_VALUE.toLong())
            builder.setMaxValue(annotation.findAnnotation<Max>()?.value ?: Int.MAX_VALUE.toLong())
        }
    }

private val LONG_TYPE_HANDLER =
    object : TypeHandler<Long>(OptionType.INTEGER) {
        override fun parse(option: OptionMapping) = option.asLong

        override fun apply(builder: OptionData, annotation: KAnnotatedElement) {
            annotation.findAnnotation<Min>()?.value?.let(builder::setMinValue)
            annotation.findAnnotation<Max>()?.value?.let(builder::setMaxValue)
        }
    }

private val BOOLEAN_TYPE_HANDLER =
    object : TypeHandler<Boolean>(OptionType.BOOLEAN) {
        override fun parse(option: OptionMapping) = option.asBoolean
    }

private val DISCORD_USER_TYPE_HANDLER =
    object : TypeHandler<User>(OptionType.USER) {
        override fun parse(option: OptionMapping) = option.asUser
    }

private val CHANNEL_TYPE_HANDLER =
    object : TypeHandler<GuildChannel>(OptionType.CHANNEL) {
        override fun parse(option: OptionMapping) = option.asChannel
    }

private val ATTACHMENT_TYPE_HANDLER =
    object : TypeHandler<Message.Attachment>(OptionType.ATTACHMENT) {
        override fun parse(option: OptionMapping) = option.asAttachment
    }

// https://github.com/Incendo/cloud/blob/fda52448c20f5537c8f03aaf6a3b844119c20463/cloud-core/src/main/java/cloud/commandframework/arguments/standard/DurationArgument.java
private val DURATION_TYPE_HANDLER =
    object : TypeHandler<Duration>(OptionType.STRING) {
        private val DURATION_PATTERN = Pattern.compile("(([1-9][0-9]+|[1-9])[dhms])")

        override fun parse(option: OptionMapping): Duration? {
            val matcher = DURATION_PATTERN.matcher(option.asString)
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

private class EnumTypeHandler<T : Enum<T>>(
    private val klass: KClass<T>,
    private val renderer: (T) -> String = { it.name.lowercase().replace("_", " ") }
) : TypeHandler<T>(OptionType.STRING) {
    override fun parse(option: OptionMapping): T? {
        return klass.java.enumConstants.firstOrNull { option.asString == it.name }
    }

    override fun apply(builder: OptionData, annotation: KAnnotatedElement) {
        klass.java.enumConstants.forEach {
            val choice = it as T
            builder.addChoice(renderer(choice), choice.name)
        }
    }
}

private class OptionParsingException(message: String) : Exception(message)

private sealed class SlashCommandNode(val path: List<String>) {

    abstract fun addOptions(vararg options: OptionData)

    abstract fun addSubcommandGroups(vararg subcommandGroups: SubcommandGroupData)

    abstract fun addSubcommands(vararg subcommands: SubcommandData)

    abstract fun setDescription(description: String)

    abstract fun setLocalizedDescription(locale: DiscordLocale, description: String)

    class Option(name: String, path: List<String>, type: OptionType) : SlashCommandNode(path) {
        val delegate = OptionData(type, name, NO_DESCRIPTION)

        override fun addOptions(vararg options: OptionData) {
            error("Can't")
        }

        override fun addSubcommandGroups(vararg subcommandGroups: SubcommandGroupData) {
            error("Can't")
        }

        override fun addSubcommands(vararg subcommands: SubcommandData) {
            error("Can't")
        }

        override fun setDescription(description: String) {
            delegate.setDescription(description)
        }

        override fun setLocalizedDescription(locale: DiscordLocale, description: String) {
            delegate.setDescriptionLocalization(locale, description)
        }
    }

    class Slash(name: String) : SlashCommandNode(listOf(name)) {
        val delegate = Commands.slash(name, NO_DESCRIPTION)

        override fun addOptions(vararg options: OptionData) {
            delegate.addOptions(options.toList())
        }

        override fun addSubcommandGroups(vararg subcommandGroups: SubcommandGroupData) {
            delegate.addSubcommandGroups(subcommandGroups.toList())
        }

        override fun addSubcommands(vararg subcommands: SubcommandData) {
            delegate.addSubcommands(subcommands.toList())
        }

        override fun setDescription(description: String) {
            delegate.setDescription(description)
        }

        override fun setLocalizedDescription(locale: DiscordLocale, description: String) {
            delegate.setDescriptionLocalization(locale, description)
        }
    }

    class Subcommand(name: String, path: List<String>) : SlashCommandNode(path) {
        val delegate = SubcommandData(name, NO_DESCRIPTION)

        override fun addOptions(vararg options: OptionData) {
            delegate.addOptions(options.toList())
        }

        override fun addSubcommandGroups(vararg subcommandGroups: SubcommandGroupData) {
            error("Can't")
        }

        override fun addSubcommands(vararg subcommands: SubcommandData) {
            error("Can't")
        }

        override fun setDescription(description: String) {
            delegate.setDescription(description)
        }

        override fun setLocalizedDescription(locale: DiscordLocale, description: String) {
            delegate.setDescriptionLocalization(locale, description)
        }
    }

    class SubcommandGroup(name: String, path: List<String>) : SlashCommandNode(path) {
        val delegate = SubcommandGroupData(name, NO_DESCRIPTION)

        override fun addOptions(vararg options: OptionData) {
            error("Can't")
        }

        override fun addSubcommandGroups(vararg subcommandGroups: SubcommandGroupData) {
            error("Can't")
        }

        override fun addSubcommands(vararg subcommands: SubcommandData) {
            delegate.addSubcommands(subcommands.toList())
        }

        override fun setDescription(description: String) {
            delegate.setDescription(description)
        }

        override fun setLocalizedDescription(locale: DiscordLocale, description: String) {
            delegate.setDescriptionLocalization(locale, description)
        }
    }
}
