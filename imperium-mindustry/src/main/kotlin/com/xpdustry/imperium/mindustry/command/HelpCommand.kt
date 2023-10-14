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
import cloud.commandframework.CommandHelpHandler
import cloud.commandframework.arguments.StaticArgument
import cloud.commandframework.meta.CommandMeta
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.annotation.Greedy
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import fr.xpdustry.distributor.api.command.ArcCommand
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.util.Players
import java.util.Locale
import kotlin.math.ceil
import mindustry.Vars

// TODO Polish the command display system
class HelpCommand : ImperiumApplication.Listener {

    @Command(["help"])
    @ClientSide
    private fun onHelpCommand(sender: CommandSender, @Greedy query: String? = null) {
        val page = if (query == null) 1 else query.toIntOrNull()
        if (page != null) {
            onHelpPageCommand(sender, page)
            return
        }

        val path = query!!.split(" ")
        val command =
            Vars.netServer.clientCommands.commandList.find { it.text == path[0] }
                ?: return sender.sendMessage("[scarlet]No command found.")
        if (command is ArcCommand<*>) {
            onArcCommandHelp(
                sender, command, command.realName + " " + path.drop(1).joinToString(" "))
        } else {
            sender.sendMessage(
                buildString {
                    appendLine(
                        "[orange]-- [lightgray]/${command.text} ${command.paramText} --[orange]")
                    val description = command.getDescription(Players.getLocale(sender.player))
                    if (description.isNotBlank()) {
                        appendLine("[white]$description")
                    }
                },
            )
        }
    }

    private fun onHelpPageCommand(sender: CommandSender, page: Int) {
        if (page < 0 || page > getPageNumber()) {
            sender.sendMessage(
                "[scarlet]'page' must be a number between[orange] 1[] and[orange] ${getPageNumber()}[scarlet].")
            return
        }
        sender.sendMessage(
            buildString {
                appendLine(
                    "[orange]-- Commands Page[lightgray] $page[gray]/[lightgray]${getPageNumber()}[orange] --")
                Vars.netServer.clientCommands.commandList
                    .asSequence()
                    .filter { it !is ArcCommand<*> || !it.isAlias }
                    .sortedBy(CommandHandler.Command::text)
                    .drop(COMMANDS_PER_PAGE * (page - 1))
                    .take(COMMANDS_PER_PAGE)
                    .forEach {
                        append("[orange] /${it.text} [white]${it.paramText}")
                        val description = it.getDescription(Players.getLocale(sender.player))
                        if (description.isNotBlank()) {
                            append("[lightgray] - $description")
                        }
                        appendLine()
                    }
            },
        )
    }

    private fun <S : Any> onArcCommandHelp(
        sender: CommandSender,
        command: ArcCommand<S>,
        query: String
    ) {
        val mapped = command.manager.commandSenderMapper.apply(sender)
        val locale = Players.getLocale(sender.player)

        when (val topic = command.manager.createCommandHelpHandler().queryHelp(mapped, query)) {
            is CommandHelpHandler.MultiHelpTopic<S> -> {
                sender.sendMessage(
                    buildString {
                        appendLine(
                            "[orange]-- Suggestions for [lightgray]${topic.longestPath}[orange] --")
                        for (suggestion in topic.childSuggestions) {
                            val parts = suggestion.split(" ")
                            appendLine(
                                "[orange] /${parts[0]} [white]${parts.drop(1).joinToString(" ")}")
                        }
                    },
                )
            }
            is CommandHelpHandler.VerboseHelpTopic<S> -> {
                sender.sendMessage(
                    buildString {
                        val syntax =
                            command.manager
                                .commandSyntaxFormatter()
                                .apply(topic.command.arguments, null)
                        appendLine("[orange]-- [lightgray]/$syntax[orange] --")
                        val description = topic.command.getDescription(locale)
                        if (description.isNotBlank()) {
                            appendLine(" [white]$description")
                        }
                        for (argument in topic.command.components.drop(1)) {
                            val argumentDescription =
                                argument.argumentDescription.getDescription(locale)
                            if (argumentDescription.isNotBlank()) {
                                appendLine(
                                    " [accent]${argument.argument.name}: [lightgray]$argumentDescription")
                            }
                        }
                    },
                )
            }
            is CommandHelpHandler.IndexHelpTopic<S> -> {
                logger.error(
                    "Impossible state: IndexHelpTopic with command ${command.text} and query $query")
                sender.sendMessage("[scarlet]No command found.")
            }
            else -> {
                logger.error("Impossible state: Unknown help topic $topic")
                sender.sendMessage("[scarlet]No command found.")
            }
        }
    }

    private fun getPageNumber() =
        ceil(Vars.netServer.clientCommands.commandList.size.toFloat() / COMMANDS_PER_PAGE).toInt()

    private fun CommandHandler.Command.getDescription(locale: Locale) =
        if (this is ArcCommand<*>) {
            manager
                .commands()
                .find { it.components[0].argument.name == realName }!!
                .getDescription(locale)
        } else {
            description
        }

    private fun cloud.commandframework.Command<*>.getDescription(locale: Locale): String {
        val cloudDescription =
            components.last { it.argument is StaticArgument<*> }.argumentDescription
        return if (cloudDescription is LocalisableArgumentDescription) {
            cloudDescription.getDescription(locale)
        } else {
            cloudDescription.description.takeUnless(String::isBlank)
                ?: commandMeta[CommandMeta.DESCRIPTION].orElse("")
        }
    }

    private fun ArgumentDescription.getDescription(locale: Locale): String =
        when (this) {
            is LocalisableArgumentDescription -> getDescription(locale)
            else -> description
        }

    companion object {
        private const val COMMANDS_PER_PAGE = 6
        private val logger by LoggerDelegate()
    }
}
