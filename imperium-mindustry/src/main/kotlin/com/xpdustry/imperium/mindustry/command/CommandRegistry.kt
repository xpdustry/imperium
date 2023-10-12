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

import arc.util.CommandHandler
import cloud.commandframework.ArgumentDescription
import cloud.commandframework.Command
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.kotlin.coroutines.SuspendingExecutionHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.command.ArcCommandManager
import fr.xpdustry.distributor.api.command.argument.PlayerArgument
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import mindustry.gen.Player
import java.util.Locale
import java.util.function.Function

interface CommandRegistry {
    fun command(name: String, vararg aliases: String = emptyArray(), block: CommandBuilder.() -> Unit)
}

data class ArgumentKey<T : Any>(val name: String, val type: TypeSpec<T>, val optional: OptionalArgument<T>?)

sealed interface TypeSpec<T : Any>
data class StringTypeSpec(val greedy: Boolean = false) : TypeSpec<String>
data class IntTypeSpec(val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE) : TypeSpec<Int>
data object PlayerTypeSpec : TypeSpec<Player>

data class OptionalArgument<T : Any>(val default: T? = null)

interface CommandContext {
    val sender: CommandSender
    operator fun <T : Any> get(key: ArgumentKey<T>): T = getOrNull(key) ?: error("Argument ${key.name} is missing")
    fun <T : Any> getOrNull(key: ArgumentKey<T>): T?
}

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class CommandBuilderMarker

@CommandBuilderMarker
interface CommandBuilder {
    fun <T : Any> argument(name: String, type: TypeSpec<T>, optional: OptionalArgument<T>? = null): ArgumentKey<T>
    fun handler(handler: suspend (CommandContext) -> Unit)
    fun subcommand(name: String, vararg aliases: String = emptyArray(), block: CommandBuilder.() -> Unit)
}

// TODO
//  - Add Help command (using interfaces)
class CloudCommandRegistry(
    plugin: MindustryPlugin,
    private val handler: CommandHandler,
    private val config: ImperiumConfig,
) : CommandRegistry, ImperiumApplication.Listener {
    private val manager = object : ArcCommandManager<CommandSender>(
        plugin,
        Function.identity(),
        Function.identity(),
        true,
    ) {
        init { setSetting(ManagerSettings.OVERRIDE_EXISTING_COMMANDS, true) }
    }

    override fun command(name: String, vararg aliases: String, block: CommandBuilder.() -> Unit) {
        val description = ImperiumArgumentDescription("imperium.command.[$name].description", config.language)
        val builder = CloudCommandBuilder(listOf(name), manager.commandBuilder(name, description, *aliases))
        builder.block()
        if (builder.register) {
            manager.command(builder.builder)
        }
    }

    override fun onImperiumInit() {
        manager.initialize(handler)
    }

    private inner class CloudCommandBuilder(var path: List<String>, var builder: Command.Builder<CommandSender>) : CommandBuilder {
        var register = false
        override fun <T : Any> argument(name: String, type: TypeSpec<T>, optional: OptionalArgument<T>?): ArgumentKey<T> {
            var argument = when (type) {
                is StringTypeSpec -> {
                    val base = StringArgument.builder<CommandSender>(name)
                    if (type.greedy) base.greedyFlagYielding() else base.single()
                }
                is IntTypeSpec -> IntegerArgument.builder<CommandSender>(name).withMin(type.min).withMax(type.max)
                is PlayerTypeSpec -> PlayerArgument.builder(name)
            }
            if (optional != null) {
                argument = argument.asOptional()
            }
            builder = builder.argument(
                argument,
                ImperiumArgumentDescription(
                    "imperium.command.[${path.joinToString(".")}].arguments.$name",
                    config.language,
                ),
            )
            return ArgumentKey(name, type, optional)
        }

        override fun handler(handler: suspend (CommandContext) -> Unit) {
            register = true
            builder = builder.handler(
                SuspendingExecutionHandler.createCommandExecutionHandler(
                    ImperiumScope.MAIN,
                    ImperiumScope.MAIN.coroutineContext,
                ) {
                    handler(CloudCommandContext(it))
                },
            )
        }

        override fun subcommand(name: String, vararg aliases: String, block: CommandBuilder.() -> Unit) {
            val path = path + listOf(name)
            val description = ImperiumArgumentDescription(
                "imperium.command.[${path.joinToString(".")}].description",
                config.language,
            )
            val subcommand = CloudCommandBuilder(path, builder.literal(name, description, *aliases))
            subcommand.block()
            if (subcommand.register) {
                manager.command(subcommand.builder)
            }
        }
    }
}

private class CloudCommandContext(private var context: cloud.commandframework.context.CommandContext<CommandSender>) : CommandContext {
    override val sender: CommandSender get() = context.sender
    override fun <T : Any> getOrNull(key: ArgumentKey<T>): T? = context.getOrDefault(key.name, key.optional?.default)
}

private data class ImperiumArgumentDescription(private val key: String, private val default: Locale) : ArgumentDescription {
    override fun getDescription(): String = DistributorProvider.get().globalLocalizationSource.format(key, default)
    fun getDescription(locale: Locale): String = DistributorProvider.get().globalLocalizationSource.format(key, locale)
}
