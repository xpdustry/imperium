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

import com.xpdustry.distributor.api.command.CommandElement
import com.xpdustry.distributor.api.command.CommandFacade
import com.xpdustry.distributor.api.command.CommandHelp
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.asList
import kotlin.math.ceil
import mindustry.Vars
import org.incendo.cloud.annotation.specifier.Greedy

class HelpCommand : ImperiumApplication.Listener {

    @ImperiumCommand(["help"])
    @ClientSide
    fun onHelpCommand(sender: CommandSender, @Greedy query: String? = null) {
        val page = if (query == null) 1 else query.toIntOrNull()
        if (page != null) {
            onHelpPageCommand(sender, page)
            return
        }

        val path = query!!.split(" ")
        val results = getCommandList(sender).filter { it.name.startsWith(path[0]) }
        if (results.isEmpty()) {
            sender.error("No command found.")
            return
        }

        val command: CommandFacade?
        if (results.size == 1) {
            command = results[0]
        } else {
            command = results.find { it.name.equals(path[0], ignoreCase = true) }
            if (command == null) {
                sender.reply(
                    buildString {
                        appendLine("[orange]-- Too many root commands found for [lightgray]${path[0]}[] --")
                        for (candidate in results) {
                            appendLine("[orange] /${candidate.name}[]")
                        }
                    }
                )
                return
            }
        }

        when (val help = command!!.getHelp(sender, path.drop(1).joinToString(" "))) {
            is CommandHelp.Empty -> sender.error("No help found.")
            is CommandHelp.Suggestion -> {
                if (help.childSuggestions.isEmpty()) {
                    sender.reply("[scarlet]No command found.")
                    logger.error("Impossible state: No suggestions for CommandHelp.Suggestion (query={})", query)
                    return
                }
                sender.reply(
                    buildString {
                        appendLine("[orange]-- Suggestions for [lightgray]${help.longestSharedPath}[orange] --")
                        for (suggestion in help.childSuggestions) {
                            val parts = suggestion.split(" ")
                            appendLine("[orange] /${parts[0]} [white]${parts.drop(1).joinToString(" ")}")
                        }
                    }
                )
            }
            is CommandHelp.Entry -> {
                sender.reply(
                    buildString {
                        appendLine("[orange]-- [lightgray]/${help.syntax}[orange] --")
                        val description = help.description.getText(sender)
                        if (description.isNotBlank()) {
                            appendLine(" [white]$description")
                        }
                        for (argument in help.arguments) {
                            if (argument.kind == CommandElement.Argument.Kind.LITERAL) continue
                            val argumentDescription = argument.description.getText(sender)
                            if (argumentDescription.isNotBlank()) {
                                appendLine(" [accent]${argument.name}: [lightgray]$argumentDescription")
                            }
                        }
                        for (flag in help.flags) {
                            val argumentDescription = flag.description.getText(sender)
                            if (argumentDescription.isNotBlank()) {
                                appendLine(" [accent]${flag.name}: [lightgray]$argumentDescription")
                            }
                        }
                    }
                )
            }
        }
    }

    private fun onHelpPageCommand(sender: CommandSender, page: Int) {
        val commands = getCommandList(sender)
        val pages = ceil(commands.size.toFloat() / COMMANDS_PER_PAGE).toInt()
        if (page < 1 || page > pages) {
            sender.reply("[scarlet]'page' must be a number between[orange] 1[] and[orange] ${pages}[scarlet].")
            return
        }
        sender.reply(
            buildString {
                appendLine("[orange]-- Commands Page[lightgray] $page[gray]/[lightgray]${pages}[orange] --")
                commands
                    .sortedBy(CommandFacade::getName)
                    .drop(COMMANDS_PER_PAGE * (page - 1))
                    .take(COMMANDS_PER_PAGE)
                    .forEach {
                        append("[orange] /${it.name}")
                        val description = it.description.getText(sender)
                        if (description.isNotBlank()) {
                            append("[lightgray] - $description")
                        }
                        appendLine()
                    }
            }
        )
    }

    private fun getCommandList(sender: CommandSender) =
        Vars.netServer.clientCommands.commandList.asList().map(CommandFacade::from).filter { it.isVisible(sender) }

    companion object {
        private const val COMMANDS_PER_PAGE = 6
        private val logger by LoggerDelegate()
    }
}
