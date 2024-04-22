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

import com.xpdustry.distributor.annotation.PluginAnnotationScanner
import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.distributor.command.DescriptionFacade
import com.xpdustry.distributor.command.cloud.ArcCommandManager
import com.xpdustry.distributor.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.name
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import io.leangen.geantyref.TypeToken
import java.util.Optional
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
import kotlinx.coroutines.future.future
import mindustry.Vars
import mindustry.server.ServerControl
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.caption.CaptionFormatter
import org.incendo.cloud.component.CommandComponent
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.description.Description
import org.incendo.cloud.execution.CommandExecutionHandler
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.permission.Permission
import org.incendo.cloud.setting.ManagerSetting
import org.incendo.cloud.translations.TranslationBundle

class CommandAnnotationScanner(plugin: MindustryPlugin, private val config: ImperiumConfig) :
    PluginAnnotationScanner<Void> {
    private val clientCommandManager = createArcCommandManager(plugin)
    private val serverCommandManager = createArcCommandManager(plugin)
    private var initialized = false

    override fun scan(instance: Any): Optional<Void> {
        if (!initialized) {
            clientCommandManager.initialize(Vars.netServer.clientCommands)
            serverCommandManager.initialize(ServerControl.instance.handler)
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
                error(
                    "Command function must be marked with either @ClientSide or @ServerSide: $function")
            }
        }
        return Optional.empty()
    }

    private fun parse(
        manager: ArcCommandManager<CommandSender>,
        container: Any,
        function: KFunction<*>,
        annotation: ImperiumCommand
    ) {
        var names = annotation.name.toNameWithAliases()
        val base = mutableListOf(names.first)

        var builder =
            manager
                .commandBuilder(names.first, createLiteralDescription(base), *names.second)
                .permission(createPermission(function, annotation))
                .commandDescription(createLiteralDescription(base))

        for (rest in annotation.path.drop(1)) {
            names = rest.toNameWithAliases()
            base += names.first
            builder = builder.literal(names.first, createLiteralDescription(base), *names.second)
        }

        for (parameter in function.parameters.drop(1)) {
            if (parameter.type.classifier == CommandSender::class) continue
            builder =
                builder.argument(
                    createCommandComponent(
                        manager, parameter, base, TypeToken.get(parameter.type.javaType)))
        }

        builder =
            builder.handler(
                CommandExecutionHandler.FutureCommandExecutionHandler { ctx ->
                    ImperiumScope.MAIN.future { callCommandFunction(container, function, ctx) }
                })

        manager.command(builder)
    }

    private fun createPermission(function: KFunction<*>, annotation: ImperiumCommand): Permission {
        val scope = function.findAnnotation<Scope>()?.gamemodes ?: emptyArray<MindustryGamemode>()
        var permission = Permission.permission("imperium.rank.${annotation.rank.name.lowercase()}")
        if (scope.isNotEmpty()) {
            permission =
                Permission.allOf(
                    permission,
                    Permission.anyOf(
                        buildList {
                            add(
                                Permission.permission(
                                    "imperium.rank.${Rank.ADMIN.name.lowercase()}"))
                            scope.forEach {
                                add(
                                    Permission.permission(
                                        "imperium.gamemode.${it.name.lowercase()}"))
                            }
                        }))
        }
        return permission
    }

    private fun <T : Any> createCommandComponent(
        manager: ArcCommandManager<CommandSender>,
        parameter: KParameter,
        base: List<String>,
        token: TypeToken<T>
    ): CommandComponent<CommandSender> {
        val parameters = manager.parserRegistry().parseAnnotations(token, parameter.annotations)
        return CommandComponent.builder<CommandSender, T>()
            .name(parameter.name!!)
            .parser(manager.parserRegistry().createParser(token, parameters).get())
            .valueType(token)
            .required(!parameter.isOptional)
            .description(createArgumentDescription(base, parameter.name!!))
            .build()
    }

    private fun createLiteralDescription(path: List<String>) =
        Description.of("imperium.command.[${path.joinToString(".")}].description")

    private fun createArgumentDescription(path: List<String>, name: String) =
        Description.of("imperium.command.[${path.joinToString(".")}].argument.$name.description")

    private suspend fun callCommandFunction(
        container: Any,
        function: KFunction<*>,
        context: CommandContext<CommandSender>
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

            val argument = context.optional<Any>(parameter.name!!).getOrNull()
            if (argument != null) {
                arguments[parameter] = argument
                continue
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

    private fun createArcCommandManager(plugin: MindustryPlugin) =
        ArcCommandManager(
                plugin, ExecutionCoordinator.asyncCoordinator(), SenderMapper.identity()) {
                    DescriptionFacade.localized(it.textDescription(), config.language)
                }
            .apply {
                settings().set(ManagerSetting.OVERRIDE_EXISTING_COMMANDS, true)
                captionRegistry().registerProvider(TranslationBundle.core(CommandSender::getLocale))
                captionFormatter(CaptionFormatter.placeholderReplacing())
                captionRegistry()
                    .registerProvider(
                        TranslationBundle.resourceBundle(
                            "com/xpdustry/imperium/mindustry/cloud_bundle",
                            CommandSender::getLocale))
            }
}
