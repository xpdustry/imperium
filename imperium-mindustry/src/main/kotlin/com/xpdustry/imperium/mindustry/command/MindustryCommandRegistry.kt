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
package com.xpdustry.imperium.mindustry.command

import cloud.commandframework.CommandManager
import cloud.commandframework.arguments.CommandArgument
import cloud.commandframework.arguments.parser.ParserParameters
import cloud.commandframework.arguments.parser.StandardParameters
import cloud.commandframework.context.CommandContext
import cloud.commandframework.keys.SimpleCloudKey
import cloud.commandframework.kotlin.coroutines.SuspendingExecutionHandler
import cloud.commandframework.permission.PredicatePermission
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.CommandRegistry
import com.xpdustry.imperium.common.command.annotation.Greedy
import com.xpdustry.imperium.common.command.annotation.Max
import com.xpdustry.imperium.common.command.annotation.Min
import com.xpdustry.imperium.common.command.name
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import fr.xpdustry.distributor.api.command.ArcCommandManager
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import io.leangen.geantyref.GenericTypeReflector
import io.leangen.geantyref.TypeToken
import java.util.function.Function
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
import kotlinx.coroutines.runBlocking
import mindustry.Vars
import mindustry.server.ServerControl

class MindustryCommandRegistry(
    plugin: MindustryPlugin,
    private val server: ServerConfig.Mindustry,
    private val config: ImperiumConfig,
    private val accounts: AccountManager
) : CommandRegistry, ImperiumApplication.Listener {
    private val clientCommandManager = createArcCommandManager(plugin)
    private val serverCommandManager = createArcCommandManager(plugin)
    private var initialized = false

    override fun parse(container: Any) {
        if (!initialized) {
            clientCommandManager.initialize(Vars.netServer.clientCommands)
            serverCommandManager.initialize(ServerControl.instance.handler)
            initialized = true
        }
        for (function in container::class.declaredMemberFunctions) {
            val annotation = function.findAnnotation<Command>() ?: continue
            function.isAccessible = true
            var marked = false
            if (function.hasAnnotation<ClientSide>()) {
                parse(clientCommandManager, container, function, annotation)
                marked = true
            }
            if (function.hasAnnotation<ServerSide>()) {
                parse(serverCommandManager, container, function, annotation)
                marked = true
            }
            if (!marked) {
                error(
                    "Command function must be marked with either @ClientSide or @ServerSide: $function")
            }
        }
    }

    private fun parse(
        manager: ArcCommandManager<CommandSender>,
        container: Any,
        function: KFunction<*>,
        annotation: Command
    ) {
        var names = annotation.name.toNameWithAliases()
        val base = mutableListOf(names.first)
        val scope = function.findAnnotation<Scope>()?.gamemodes?.toSet() ?: emptySet()
        var builder =
            manager
                .commandBuilder(names.first, createLiteralDescription(base), *names.second)
                .permission(createPermission(annotation.rank, names.first, scope))
        for (rest in annotation.path.drop(1)) {
            names = rest.toNameWithAliases()
            base += names.first
            builder = builder.literal(names.first, createLiteralDescription(base), *names.second)
        }
        for (argument in function.parameters.drop(1)) {
            if (argument.type.classifier == CommandSender::class) continue
            builder =
                builder.argument(
                    createCommandArgument(manager, argument, TypeToken.get(argument.type.javaType)),
                    createArgumentDescription(base, argument.name!!),
                )
        }
        builder =
            builder.handler(
                SuspendingExecutionHandler.createCommandExecutionHandler(
                    ImperiumScope.MAIN,
                    ImperiumScope.MAIN.coroutineContext,
                ) { ctx ->
                    callCommandFunction(container, function, ctx)
                },
            )
        manager.command(builder)
    }

    private fun createPermission(rank: Rank, command: String, scope: Set<MindustryGamemode>) =
        PredicatePermission.of<CommandSender>(SimpleCloudKey.of("imperium:$command")) { sender ->
            if (sender.isConsole) return@of true

            runBlocking {
                val current = accounts.findByIdentity(sender.player.identity)?.rank ?: Rank.EVERYONE
                current >= Rank.ADMIN ||
                    (current >= rank && (scope.isEmpty() || server.gamemode in scope))
            }
        }

    private fun <T : Any> createCommandArgument(
        manager: ArcCommandManager<CommandSender>,
        parameter: KParameter,
        token: TypeToken<T>
    ): CommandArgument<CommandSender, T> {
        val parameters = manager.parserRegistry().parseAnnotations(token, parameter.annotations)
        return CommandArgument.ofType<CommandSender, T>(token, parameter.name!!)
            .withParser(manager.parserRegistry().createParser(token, parameters).get())
            .run { if (parameter.isOptional) asOptional() else asRequired() }
            .build()
    }

    private fun createLiteralDescription(path: List<String>) =
        LocalisableArgumentDescription(
            "imperium.command.[${path.joinToString(".")}].description",
            config.language,
        )

    private fun createArgumentDescription(path: List<String>, name: String) =
        LocalisableArgumentDescription(
            "imperium.command.[${path.joinToString(".")}].argument.$name.description",
            config.language,
        )

    private suspend fun callCommandFunction(
        container: Any,
        function: KFunction<*>,
        context: CommandContext<CommandSender>
    ) {
        val arguments = mutableMapOf<KParameter, Any>()
        for (parameter in function.parameters) {
            if (parameter.index == 0) {
                arguments[parameter] = container
                continue
            }

            if (CommandSender::class.isSuperclassOf(parameter.type.classifier!! as KClass<*>)) {
                arguments[parameter] = context.sender
                continue
            }

            val argument = context.getOptional<Any>(parameter.name!!).getOrNull()
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
    }

    private fun String.toNameWithAliases(): Pair<String, Array<String>> {
        val parts = split("|")
        return parts[0] to parts.drop(1).toTypedArray()
    }
}

private fun createArcCommandManager(plugin: MindustryPlugin) =
    ArcCommandManager(
            plugin,
            Function.identity(),
            Function.identity(),
            true,
        )
        .apply {
            setSetting(CommandManager.ManagerSettings.OVERRIDE_EXISTING_COMMANDS, true)
            setSetting(CommandManager.ManagerSettings.ENFORCE_INTERMEDIARY_PERMISSIONS, false)

            parserRegistry().registerAnnotationMapper<Greedy, String>(Greedy::class.java) { _, _ ->
                ParserParameters.single(StandardParameters.GREEDY, true)
            }
            parserRegistry().registerAnnotationMapper<Min, Number>(Min::class.java) {
                annotation,
                token ->
                val number =
                    getNumber(annotation.value, token)
                        ?: return@registerAnnotationMapper ParserParameters.empty()
                ParserParameters.single(StandardParameters.RANGE_MIN, number)
            }
            parserRegistry().registerAnnotationMapper<Max, Number>(Max::class.java) {
                annotation,
                token ->
                val number =
                    getNumber(annotation.value, token)
                        ?: return@registerAnnotationMapper ParserParameters.empty()
                ParserParameters.single(StandardParameters.RANGE_MAX, number)
            }
        }

private fun getNumber(value: Number, token: TypeToken<*>): Number? =
    when (GenericTypeReflector.erase(token.type).kotlin) {
        Byte::class -> value.toByte()
        Short::class -> value.toShort()
        Int::class -> value.toInt()
        Long::class -> value.toLong()
        Float::class -> value.toFloat()
        Double::class -> value.toDouble()
        else -> null
    }
