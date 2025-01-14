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
package com.xpdustry.imperium.mindustry.command

import arc.util.CommandHandler
import com.xpdustry.distributor.api.annotation.PluginAnnotationProcessor
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.command.DescriptionFacade
import com.xpdustry.distributor.api.command.cloud.MindustryCommandManager
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.name
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Flag
import com.xpdustry.imperium.mindustry.command.annotation.RequireAchievement
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import io.leangen.geantyref.TypeToken
import java.util.Optional
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.time.Duration as KotlinDuration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.future.future
import mindustry.Vars
import mindustry.server.ServerControl
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.component.TypedCommandComponent
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.description.Description
import org.incendo.cloud.execution.CommandExecutionHandler
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.parser.ParserDescriptor
import org.incendo.cloud.parser.flag.CommandFlag
import org.incendo.cloud.parser.standard.DurationParser
import org.incendo.cloud.permission.Permission
import org.incendo.cloud.setting.ManagerSetting
import org.incendo.cloud.translations.TranslationBundle

class CommandAnnotationScanner(private val plugin: MindustryPlugin, private val config: ImperiumConfig) :
    PluginAnnotationProcessor<Void> {
    private lateinit var clientCommandManager: MindustryCommandManager<CommandSender>
    private lateinit var serverCommandManager: MindustryCommandManager<CommandSender>
    private var initialized = false

    override fun process(instance: Any): Optional<Void> {
        if (!initialized) {
            clientCommandManager = createArcCommandManager(Vars.netServer.clientCommands)
            serverCommandManager = createArcCommandManager(ServerControl.instance.handler)
            initialized = true
        }
        for (function in instance::class.declaredMemberFunctions) {
            val annotation = function.findAnnotation<ImperiumCommand>() ?: continue
            function.isAccessible = true
            var marked = false
            if (function.hasAnnotation<ClientSide>()) {
                parse(clientCommandManager, instance, function, annotation)
                marked = true
            }
            if (function.hasAnnotation<ServerSide>()) {
                parse(serverCommandManager, instance, function, annotation)
                marked = true
            }
            if (!marked) {
                error("Command function must be marked with either @ClientSide or @ServerSide: $function")
            }
        }
        return Optional.empty()
    }

    private fun parse(
        manager: MindustryCommandManager<CommandSender>,
        container: Any,
        function: KFunction<*>,
        annotation: ImperiumCommand,
    ) {
        var names = annotation.name.toNameWithAliases()
        val path = mutableListOf(names.first)
        var builder =
            manager
                .commandBuilder(names.first, createLiteralDescription(path), *names.second)
                .permission(createPermission(function, annotation))

        for (rest in annotation.path.drop(1)) {
            names = rest.toNameWithAliases()
            path += names.first
            builder = builder.literal(names.first, createLiteralDescription(path), *names.second)
        }

        builder = builder.commandDescription(createLiteralDescription(path))

        for (parameter in function.parameters.drop(1)) {
            if (parameter.type.classifier == CommandSender::class) continue
            val flag = parameter.findAnnotation<Flag>()
            builder =
                if (flag == null) {
                    builder.argument(createCommandComponent<Any>(manager, parameter, path))
                } else {
                    builder.flag(createFlagComponent<Any>(manager, parameter, path, flag))
                }
        }

        builder =
            builder.handler(
                CommandExecutionHandler.FutureCommandExecutionHandler { ctx ->
                    ImperiumScope.MAIN.future { callCommandFunction(container, function, ctx) }
                }
            )

        manager.command(builder)
    }

    private fun createPermission(function: KFunction<*>, annotation: ImperiumCommand): Permission {
        var permission = Permission.permission("imperium.rank.${annotation.rank.name.lowercase()}")

        val achievement = function.findAnnotation<RequireAchievement>()?.achievement
        if (achievement != null) {
            permission =
                Permission.allOf(
                    permission,
                    Permission.permission("imperium.achievement.${achievement.name.lowercase()}"),
                )
        }

        val scope = function.findAnnotation<Scope>()?.gamemodes ?: emptyArray<MindustryGamemode>()
        if (scope.isNotEmpty()) {
            permission =
                Permission.allOf(
                    permission,
                    Permission.anyOf(scope.map { Permission.permission("imperium.gamemode.${it.name.lowercase()}") }),
                )
        }

        if (annotation.rank != Rank.OWNER) {
            permission =
                Permission.anyOf(permission, Permission.permission("imperium.rank.${Rank.ADMIN.name.lowercase()}"))
        }

        return permission
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> createCommandComponent(
        manager: MindustryCommandManager<CommandSender>,
        parameter: KParameter,
        path: List<String>,
    ): TypedCommandComponent<CommandSender, T> {
        val token = TypeToken.get(parameter.type.javaType) as TypeToken<T>
        val parameters = manager.parserRegistry().parseAnnotations(token, parameter.annotations)
        return TypedCommandComponent.builder<CommandSender, T>()
            .name(parameter.name!!)
            .parser(
                manager.parserRegistry().createParser(token, parameters).getOrNull()
                    ?: error("No parser found for type: ${parameter.type.javaType}")
            )
            .valueType(token)
            .required(!parameter.isOptional)
            .description(createArgumentDescription(path, parameter.name!!))
            .commandManager(manager)
            .build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> createFlagComponent(
        manager: MindustryCommandManager<CommandSender>,
        parameter: KParameter,
        path: List<String>,
        flag: Flag,
    ): CommandFlag<T> {
        if (parameter.type.classifier != Boolean::class && !parameter.isOptional) {
            throw IllegalArgumentException("A value flag must be optional: ${parameter.name}")
        }
        val builder =
            CommandFlag.builder<CommandSender>(parameter.name!!)
                .withAliases(if (flag.alias.isNotBlank()) listOf(flag.alias) else emptyList())
                .withDescription(createArgumentDescription(path, parameter.name!!))
        return if (parameter.type.classifier == Boolean::class && !parameter.isOptional) {
            builder.build() as CommandFlag<T>
        } else {
            builder.withComponent(createCommandComponent<T>(manager, parameter, path)).build()
        }
    }

    private fun createLiteralDescription(path: List<String>) =
        Description.of("imperium.command.[${path.joinToString(".")}].description")

    private fun createArgumentDescription(path: List<String>, name: String) =
        Description.of("imperium.command.[${path.joinToString(".")}].argument.$name.description")

    private suspend fun callCommandFunction(
        container: Any,
        function: KFunction<*>,
        context: CommandContext<CommandSender>,
    ): Void? {
        val arguments = mutableMapOf<KParameter, Any>()
        for (parameter in function.parameters) {
            if (parameter.index == 0) {
                arguments[parameter] = container
                continue
            }

            if (CommandSender::class.isSuperclassOf(parameter.type.classifier!! as KClass<*>)) {
                arguments[parameter] = context.sender()
                continue
            }

            if (parameter.hasAnnotation<Flag>()) {
                if (parameter.type.classifier == Boolean::class && !parameter.isOptional) {
                    arguments[parameter] = context.flags().hasFlag(parameter.name!!)
                    continue
                }
                val argument = context.flags().get<Any>(parameter.name!!)
                if (argument != null) {
                    arguments[parameter] = argument
                    continue
                }
            } else {
                val argument = context.optional<Any>(parameter.name!!).getOrNull()
                if (argument != null) {
                    arguments[parameter] = argument
                    continue
                }
            }

            if (!parameter.isOptional) {
                throw IllegalArgumentException("Missing required parameter: ${parameter.name}")
            }
        }

        if (function.isSuspend) {
            function.callSuspendBy(arguments)
        } else {
            runMindustryThread { function.callBy(arguments) }
        }
        return null
    }

    private fun String.toNameWithAliases(): Pair<String, Array<String>> {
        val parts = split("|")
        return parts[0] to parts.drop(1).toTypedArray()
    }

    private fun createArcCommandManager(handler: CommandHandler) =
        MindustryCommandManager(plugin, handler, ExecutionCoordinator.asyncCoordinator(), SenderMapper.identity())
            .apply {
                descriptionMapper { DescriptionFacade.translated(it.textDescription(), config.language) }
                settings().set(ManagerSetting.OVERRIDE_EXISTING_COMMANDS, true)
                captionRegistry().registerProvider(TranslationBundle.core(CommandSender::getLocale))
                parserRegistry().registerParser(KotlinDurationParser())
            }

    @Suppress("FunctionName")
    private fun KotlinDurationParser(): ParserDescriptor<CommandSender, KotlinDuration> {
        val parser = DurationParser.durationParser<CommandSender>()
        return parser.mapSuccess(KotlinDuration::class.java) { _, result ->
            CompletableFuture.completedFuture(result.toKotlinDuration())
        }
    }
}
