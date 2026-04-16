// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.command

import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.Lowercase
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.control.RemoteActionMessage
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.security.PunishmentDuration
import com.xpdustry.imperium.discord.command.annotation.AlsoAllow
import com.xpdustry.imperium.discord.command.annotation.Range
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
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User as DiscordUser
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.interactions.components.ComponentInteraction
import net.dv8tion.jda.api.interactions.modals.ModalInteraction

@Inject
class DiscordCommandDispatcher(private val discord: DiscordService, private val config: ImperiumConfig) :
    AnnotationScanner {
    private val typeHandlers = mutableMapOf<KClass<*>, TypeHandler<*>>()
    private val slashTree = CommandNode("root", parent = null)
    private val menuHandlers = mutableMapOf<String, InteractionHandler<out ComponentInteraction>>()
    private val modalHandlers = mutableMapOf<String, InteractionHandler<out ModalInteraction>>()

    init {
        registerHandler(String::class, STRING_TYPE_HANDLER)
        registerHandler(Int::class, INT_TYPE_HANDLER)
        registerHandler(Long::class, LONG_TYPE_HANDLER)
        registerHandler(Boolean::class, BOOLEAN_TYPE_HANDLER)
        registerHandler(DiscordUser::class, DISCORD_USER_TYPE_HANDLER)
        registerHandler(GuildChannelUnion::class, CHANNEL_TYPE_HANDLER)
        registerHandler(Message.Attachment::class, ATTACHMENT_TYPE_HANDLER)
        registerHandler(Duration::class, DURATION_TYPE_HANDLER)
        registerHandler(PunishmentDuration::class, EnumTypeHandler(PunishmentDuration::class))
        registerHandler(MindustryGamemode::class, EnumTypeHandler(MindustryGamemode::class))
        registerHandler(Achievement::class, EnumTypeHandler(Achievement::class))
        registerHandler(Rank::class, EnumTypeHandler(Rank::class))
        registerHandler(RemoteActionMessage.Action::class, EnumTypeHandler(RemoteActionMessage.Action::class))
    }

    override fun scan(instance: Any) {
        registerSlashCommands(instance)
        registerMenuCommands(instance)
        registerModalCommands(instance)
    }

    override fun process() {
        compileSlashCommands()

        discord.jda.addSuspendingEventListener<SlashCommandInteractionEvent> { event ->
            handleSlashCommand(event.interaction)
        }

        discord.jda.addSuspendingEventListener<GenericComponentInteractionCreateEvent> { event ->
            handleInteraction(
                id = event.componentId,
                interaction = event.interaction,
                handlers = menuHandlers,
                missingMessage = "This interaction is no longer valid",
                unexpectedMessage = "Unexpected interaction type, please report this to a moderator",
                executionMessage = "**:warning: An unexpected error occurred while executing this interaction.**",
                permissionMessage = ":warning: **You do not have permission to use this command.**",
            )
        }

        discord.jda.addSuspendingEventListener<ModalInteractionEvent> { event ->
            handleInteraction(
                id = event.modalId,
                interaction = event.interaction,
                handlers = modalHandlers,
                missingMessage = "This modal is no longer valid",
                unexpectedMessage = "Unexpected modal type, please report this to a moderator",
                executionMessage = "**:warning: An unexpected error occurred while handling this modal.**",
            )
        }
    }

    private fun registerSlashCommands(container: Any) {
        for (function in container::class.memberFunctions) {
            val command = function.findAnnotation<ImperiumCommand>() ?: continue
            command.validate()
            requireSuspending(function)

            val arguments = mutableListOf<SlashCommandHandler.Argument<*>>()
            var wasOptional = false
            for (parameter in function.parameters) {
                if (parameter.index == 0) {
                    continue
                }

                if (parameter.name!!.lowercase() != parameter.name) {
                    throw IllegalArgumentException("$function parameter names must be lowercase: ${parameter.name}")
                }

                if (parameter.index == 1) {
                    if (!isSupportedSlashActor(parameter.type.classifier!!)) {
                        throw IllegalArgumentException("$function first parameter is not a slash command interaction")
                    }
                    continue
                }

                val optional = parameter.isOptional || parameter.type.isMarkedNullable
                if (wasOptional && !optional) {
                    throw IllegalArgumentException(
                        "$function optional parameters must be at the end of the parameter list."
                    )
                }
                wasOptional = optional

                val classifier = parameter.type.classifier
                if (classifier !is KClass<*> || classifier !in typeHandlers) {
                    throw IllegalArgumentException("$function has unsupported parameter type $classifier")
                }

                arguments += createSlashArgument(parameter, optional, classifier)
            }

            function.isAccessible = true
            val permission = createPermission(function, command.rank)
            slashTree.resolve(command.path.toList(), SlashCommandHandler(container, function, permission, arguments))
        }
    }

    private fun registerMenuCommands(container: Any) {
        for (function in container::class.memberFunctions) {
            val command = function.findAnnotation<MenuCommand>() ?: continue
            registerInteractionHandler(
                container = container,
                function = function,
                name = command.name,
                expectedType = ComponentInteraction::class,
                permission = createPermission(function, command.rank),
                handlers = menuHandlers,
                kind = "interaction",
            )
        }
    }

    private fun registerModalCommands(container: Any) {
        for (function in container::class.memberFunctions) {
            val command = function.findAnnotation<ModalCommand>() ?: continue
            registerInteractionHandler(
                container = container,
                function = function,
                name = command.name,
                expectedType = ModalInteraction::class,
                permission = ALLOW_ALL,
                handlers = modalHandlers,
                kind = "modal",
            )
        }
    }

    private fun <T : Interaction> registerInteractionHandler(
        container: Any,
        function: KFunction<*>,
        name: String,
        expectedType: KClass<T>,
        permission: PermissionPredicate,
        handlers: MutableMap<String, InteractionHandler<out T>>,
        kind: String,
    ) {
        validateInteractionName(name, function, kind)
        requireSuspending(function)

        if (function.parameters.size != 2) {
            throw IllegalArgumentException("$function $kind must have exactly one interaction parameter")
        }

        val classifier = function.parameters[1].type.classifier
        val parameterType =
            classifier as? KClass<*> ?: throw IllegalArgumentException("$function has no concrete interaction type")
        if (!parameterType.isSubclassOf(expectedType)) {
            throw IllegalArgumentException("$function $kind parameter must extend ${expectedType.simpleName}")
        }

        if (name in handlers) {
            throw IllegalArgumentException("$function $kind $name is already registered")
        }

        function.isAccessible = true
        handlers[name] = InteractionHandler(container, permission, function, parameterType)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun handleSlashCommand(interaction: SlashCommandInteraction) {
        val node =
            try {
                slashTree.resolve(interaction.fullCommandName.split(" "))
            } catch (e: Exception) {
                LOGGER.error("Unknown slash command {}", interaction.fullCommandName, e)
                interaction.deferReply(true).await().sendMessage(UNKNOWN_COMMAND_MESSAGE).await()
                return
            }

        val command = node.edge
        if (command == null) {
            LOGGER.error("Resolved slash command {} has no handler", interaction.fullCommandName)
            interaction.deferReply(true).await().sendMessage(UNKNOWN_COMMAND_MESSAGE).await()
            return
        }

        if (!command.permission.test(interaction)) {
            interaction.deferReply(true).await().sendMessage(PERMISSION_DENIED_MESSAGE).await()
            return
        }

        val arguments = mutableMapOf<KParameter, Any>()
        try {
            for (parameter in command.function.parameters) {
                if (parameter.index == 0) {
                    arguments[parameter] = command.container
                    continue
                }

                if (isSupportedSlashActor(parameter.type.classifier!!)) {
                    arguments[parameter] = interaction
                    continue
                }

                val argument = command.argumentsByName[parameter.name!!]!!
                val parsed =
                    try {
                        interaction.getOption(argument.name)?.let { option ->
                            argument.handler.parse(option, parameter)
                        }
                    } catch (e: OptionParsingException) {
                        interaction
                            .deferReply(true)
                            .await()
                            .sendMessage(":warning: Failed to parse the **${argument.name}** argument: ${e.message}")
                            .await()
                        return
                    }

                if (parsed != null) {
                    arguments[parameter] = parsed
                    continue
                }

                if (!argument.optional) {
                    throw IllegalArgumentException("Missing required parameter: ${parameter.name}")
                }
            }
        } catch (e: Exception) {
            LOGGER.error("Error while parsing arguments for command ${node.fullName}", e)
            interaction.deferReply(true).await().sendMessage(PARSE_ERROR_MESSAGE).await()
            return
        }

        try {
            command.function.callSuspendBy(arguments)
        } catch (e: Exception) {
            LOGGER.error("Error while executing command ${node.fullName}", e)
            try {
                interaction.deferReply(true).await()
            } catch (_: Exception) {}
            interaction.hook.sendMessage(EXECUTION_ERROR_MESSAGE).await()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> handleInteraction(
        id: String,
        interaction: T,
        handlers: Map<String, InteractionHandler<out T>>,
        missingMessage: String,
        unexpectedMessage: String,
        executionMessage: String,
        permissionMessage: String? = null,
    ) where T : Interaction, T : IReplyCallback {
        val handler = handlers[id]
        if (handler == null) {
            interaction.deferReply(true).await().sendMessage(missingMessage).await()
            return
        }

        if (!handler.type.isInstance(interaction)) {
            LOGGER.error(
                "Unexpected interaction type for {}, expected {}, got {}",
                id,
                handler.type,
                interaction::class,
            )
            interaction.deferReply(true).await().sendMessage(unexpectedMessage).await()
            return
        }

        if (permissionMessage != null && !handler.permission.test(interaction)) {
            interaction.deferReply(true).await().sendMessage(permissionMessage).await()
            return
        }

        try {
            handler.function.callSuspend(handler.container, interaction)
        } catch (e: Exception) {
            LOGGER.error("Error while executing interaction {}", id, e)
            try {
                interaction.deferReply(true).await()
            } catch (_: Exception) {}
            interaction.hook.sendMessage(executionMessage).await()
        }
    }

    private fun compileSlashCommands() {
        val compiled = mutableSetOf<SlashCommandData>()
        for (entry in slashTree.children) {
            val wrapper = SlashCommandNode.Slash(entry.key)
            compileSlashNode(wrapper, entry.value)
            wrapper.applyBuilderTranslations()
            compiled += wrapper.delegate
        }

        runBlocking {
            if (config.discord.globalCommands) {
                discord.jda.updateCommands().addCommands(compiled).await()
                discord.getMainServer().retrieveCommands().await().forEach {
                    ImperiumScope.MAIN.launch { discord.getMainServer().deleteCommandById(it.idLong).await() }
                }
            } else {
                discord.getMainServer().updateCommands().addCommands(compiled).await()
            }
        }
    }

    private fun compileSlashNode(parent: SlashCommandNode, node: CommandNode) {
        when (node.height) {
            0 -> throw IllegalStateException("Root node cannot be compiled.")
            1 -> {
                for (argument in node.edge?.arguments ?: emptyList()) {
                    val wrapper = SlashCommandNode.Option(argument.name, parent.path, argument.handler.type)
                    wrapper.delegate.isRequired = !argument.optional
                    argument.handler.apply(wrapper.delegate, argument.annotations, argument.optional)
                    wrapper.applyBuilderTranslations()
                    parent.addOptions(wrapper.delegate)
                }
            }

            else -> {
                for (child in node.children.values) {
                    when (child.height) {
                        1 -> {
                            val wrapper = SlashCommandNode.Subcommand(child.name, parent.path + child.name)
                            compileSlashNode(wrapper, child)
                            wrapper.applyBuilderTranslations()
                            parent.addSubcommands(wrapper.delegate)
                        }

                        2 -> {
                            val wrapper = SlashCommandNode.SubcommandGroup(child.name, parent.path + child.name)
                            compileSlashNode(wrapper, child)
                            wrapper.applyBuilderTranslations()
                            parent.addSubcommandGroups(wrapper.delegate)
                        }

                        else -> error("The slash command tree is too deep")
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> createSlashArgument(
        parameter: KParameter,
        optional: Boolean,
        klass: KClass<T>,
    ): SlashCommandHandler.Argument<T> {
        val handler =
            typeHandlers[klass] as TypeHandler<T>? ?: throw IllegalArgumentException("Unsupported type $klass")
        return SlashCommandHandler.Argument(parameter.name!!, optional, parameter, handler)
    }

    private fun createPermission(function: KFunction<*>, rank: Rank): PermissionPredicate {
        var permission = PermissionPredicate { discord.isAllowed(it.user, rank) }
        val allow = function.findAnnotation<AlsoAllow>()
        if (allow != null) {
            val previous = permission
            permission = PermissionPredicate { previous.test(it) || discord.isAllowed(it.user, allow.permission) }
        }
        return permission
    }

    private fun validateInteractionName(name: String, function: KFunction<*>, kind: String) {
        if (!name.all { it.isLetterOrDigit() || it == '-' || it == ':' }) {
            throw IllegalArgumentException("$function $kind name must be alphanumeric")
        }
    }

    private fun requireSuspending(function: KFunction<*>) {
        if (!function.isSuspend) {
            throw IllegalArgumentException("$function must be suspend")
        }
    }

    private fun isSupportedSlashActor(classifier: KClassifier) =
        classifier == Interaction::class || classifier == SlashCommandInteraction::class

    private fun <T : Any> registerHandler(klass: KClass<T>, handler: TypeHandler<T>) {
        typeHandlers[klass] = handler
    }

    private fun SlashCommandNode.applyBuilderTranslations() {
        var key = "imperium.command.[${path.joinToString(".")}]"
        key += if (this is SlashCommandNode.Option) ".argument.${delegate.name}.description" else ".description"

        setDescription(getTranslatedTextOrNull(key, config.language) ?: NO_DESCRIPTION)
        for (language in config.supportedLanguages) {
            getTranslatedTextOrNull(key, language)?.let { description ->
                val locale = language.toDiscordLocale() ?: return@let
                setLocalizedDescription(locale, description)
            }
        }
    }

    private fun ImperiumCommand.validate() {
        if (path.isEmpty()) {
            throw IllegalArgumentException("Command name cannot be empty")
        }
        if (path.any { !it.matches(Regex("^[a-zA-Z](-?[a-zA-Z0-9])*$")) }) {
            throw IllegalArgumentException("Command name must be alphanumeric and start with a letter")
        }
    }

    companion object {
        private val LOGGER by LoggerDelegate()
        private val ALLOW_ALL = PermissionPredicate { true }
    }
}

private const val NO_DESCRIPTION = "No description."
private const val UNKNOWN_COMMAND_MESSAGE = ":warning: **This command is no longer available.**"
private const val PERMISSION_DENIED_MESSAGE = ":warning: **You do not have permission to use this command.**"
private const val PARSE_ERROR_MESSAGE = ":warning: **An unexpected error occurred while parsing your command.**"
private const val EXECUTION_ERROR_MESSAGE = ":warning: **An unexpected error occurred while executing your command.**"

private data class CommandNode(val name: String, val parent: CommandNode?) {
    val children = mutableMapOf<String, CommandNode>()
    var edge: SlashCommandHandler? = null

    val fullName: String
        get() = if (parent == null) name else "${parent.fullName}.$name"

    val depth: Int
        get() {
            var count = 0
            var node: CommandNode? = parent
            while (node != null) {
                count++
                node = node.parent
            }
            return count
        }

    val height: Int
        get() = 1 + (children.maxOfOrNull { it.value.height } ?: 0)

    fun resolve(path: List<String>, edge: SlashCommandHandler? = null): CommandNode {
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

private data class SlashCommandHandler(
    val container: Any,
    val function: KFunction<*>,
    val permission: PermissionPredicate,
    val arguments: List<Argument<*>>,
) {
    val argumentsByName = arguments.associateBy(Argument<*>::name)

    data class Argument<T : Any>(
        val name: String,
        val optional: Boolean,
        val annotations: KAnnotatedElement,
        val handler: TypeHandler<T>,
    )
}

private data class InteractionHandler<T : Interaction>(
    val container: Any,
    val permission: PermissionPredicate,
    val function: KFunction<*>,
    val type: KClass<*>,
)

abstract class TypeHandler<T : Any>(val type: OptionType) {
    abstract fun parse(option: OptionMapping, annotations: KAnnotatedElement): T?

    open fun apply(builder: OptionData, annotation: KAnnotatedElement, optional: Boolean) = Unit
}

private val STRING_TYPE_HANDLER =
    object : TypeHandler<String>(OptionType.STRING) {
        override fun parse(option: OptionMapping, annotations: KAnnotatedElement): String {
            var result = option.asString
            if (annotations.hasAnnotation<Lowercase>()) result = result.lowercase()
            return result
        }

        override fun apply(builder: OptionData, annotation: KAnnotatedElement, optional: Boolean) {
            annotation.findAnnotation<Range>()?.apply {
                if (min.isNotEmpty()) builder.setMinLength(min.toInt())
                if (max.isNotEmpty()) builder.setMaxLength(max.toInt())
            }
        }
    }

private val INT_TYPE_HANDLER =
    object : TypeHandler<Int>(OptionType.INTEGER) {
        override fun parse(option: OptionMapping, annotations: KAnnotatedElement) = option.asInt

        override fun apply(builder: OptionData, annotation: KAnnotatedElement, optional: Boolean) {
            annotation.findAnnotation<Range>()?.apply {
                builder.setMinValue(if (min.isNotEmpty()) min.toLong() else Int.MIN_VALUE.toLong())
                builder.setMaxValue(if (max.isNotEmpty()) max.toLong() else Int.MAX_VALUE.toLong())
            }
        }
    }

private val LONG_TYPE_HANDLER =
    object : TypeHandler<Long>(OptionType.INTEGER) {
        override fun parse(option: OptionMapping, annotations: KAnnotatedElement) = option.asLong

        override fun apply(builder: OptionData, annotation: KAnnotatedElement, optional: Boolean) {
            annotation.findAnnotation<Range>()?.apply {
                if (min.isNotEmpty()) builder.setMinValue(min.toLong())
                if (max.isNotEmpty()) builder.setMaxValue(max.toLong())
            }
        }
    }

private val BOOLEAN_TYPE_HANDLER =
    object : TypeHandler<Boolean>(OptionType.BOOLEAN) {
        override fun parse(option: OptionMapping, annotations: KAnnotatedElement) = option.asBoolean
    }

private val DISCORD_USER_TYPE_HANDLER =
    object : TypeHandler<User>(OptionType.USER) {
        override fun parse(option: OptionMapping, annotations: KAnnotatedElement) = option.asUser
    }

private val CHANNEL_TYPE_HANDLER =
    object : TypeHandler<GuildChannelUnion>(OptionType.CHANNEL) {
        override fun parse(option: OptionMapping, annotations: KAnnotatedElement) = option.asChannel
    }

private val ATTACHMENT_TYPE_HANDLER =
    object : TypeHandler<Message.Attachment>(OptionType.ATTACHMENT) {
        override fun parse(option: OptionMapping, annotations: KAnnotatedElement) = option.asAttachment
    }

private val DURATION_TYPE_HANDLER =
    object : TypeHandler<Duration>(OptionType.STRING) {
        private val durationPattern = Pattern.compile("(([1-9][0-9]+|[1-9])[dhms])")

        override fun parse(option: OptionMapping, annotations: KAnnotatedElement): Duration {
            val matcher = durationPattern.matcher(option.asString)
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
    private val renderer: (T) -> String = { it.name.lowercase().replace("_", " ") },
) : TypeHandler<T>(OptionType.STRING) {
    override fun parse(option: OptionMapping, annotations: KAnnotatedElement): T? {
        return klass.java.enumConstants.firstOrNull { option.asString == it.name }
    }

    override fun apply(builder: OptionData, annotation: KAnnotatedElement, optional: Boolean) {
        klass.java.enumConstants.forEach { builder.addChoice(renderer(it), it.name) }
        if (optional) {
            builder.addChoice("none", "none")
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
