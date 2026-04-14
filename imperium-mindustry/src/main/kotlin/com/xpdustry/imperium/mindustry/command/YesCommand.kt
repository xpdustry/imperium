// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.command

import arc.util.CommandHandler
import arc.util.Strings
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.translation.command_unknown
import com.xpdustry.imperium.mindustry.translation.command_yes_confirm
import com.xpdustry.imperium.mindustry.translation.command_yes_failure_no_pending
import mindustry.Vars
import mindustry.game.EventType

class YesCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val plugin = instances.get<MindustryPlugin>()
    private val lastInputs = PlayerMap<String>(plugin)
    private val pendingCommands = PlayerMap<String>(plugin)

    override fun onImperiumInit() {
        val delegate = Vars.netServer.invalidHandler
        Vars.netServer.invalidHandler =
            mindustry.core.NetServer.InvalidCommandHandler { player, response ->
                if (response.type == CommandHandler.ResponseType.unknownCommand) {
                    pendingCommands.remove(player)
                    val input = lastInputs[player]
                    val suggestion = findSuggestion(response.runCommand)
                    if (input != null && suggestion != null) {
                        val command = replaceCommand(input, suggestion.text)
                        pendingCommands[player] = command
                        player.asAudience.sendMessage(command_yes_confirm(command))
                        null
                    } else {
                        player.asAudience.sendMessage(command_unknown())
                        null
                    }
                } else {
                    pendingCommands.remove(player)
                    delegate.handle(player, response)
                }
            }
    }

    @EventHandler
    fun onPlayerChat(event: EventType.PlayerChatEvent) {
        val prefix = Vars.netServer.clientCommands.prefix
        if (!event.message.startsWith(prefix)) {
            pendingCommands.remove(event.player)
            return
        }
        lastInputs[event.player] = event.message
        if (!isYesCommand(event.message, prefix)) {
            pendingCommands.remove(event.player)
        }
    }

    @ImperiumCommand(["yes"])
    @ClientSide
    fun onYesCommand(sender: CommandSender) {
        val command = pendingCommands.remove(sender.player)
        if (command == null) {
            sender.reply(command_yes_failure_no_pending())
            return
        }
        val response = Vars.netServer.clientCommands.handleMessage(command, sender.player)
        if (
            response.type != CommandHandler.ResponseType.valid && response.type != CommandHandler.ResponseType.noCommand
        ) {
            Vars.netServer.invalidHandler.handle(sender.player, response)?.let(sender.player::sendMessage)
        }
    }

    private fun findSuggestion(command: String): CommandHandler.Command? {
        var minDistance = 0
        var closest: CommandHandler.Command? = null
        for (candidate in Vars.netServer.clientCommands.commandList) {
            val distance = Strings.levenshtein(candidate.text, command)
            if (distance < 3 && (closest == null || distance < minDistance)) {
                minDistance = distance
                closest = candidate
            }
        }
        return closest
    }

    private fun replaceCommand(input: String, replacement: String): String {
        val prefix = Vars.netServer.clientCommands.prefix
        val body = input.removePrefix(prefix)
        val separator = body.indexOf(' ')
        return if (separator == -1) {
            "$prefix$replacement"
        } else {
            "$prefix$replacement${body.substring(separator)}"
        }
    }

    private fun isYesCommand(input: String, prefix: String): Boolean {
        val body = input.removePrefix(prefix)
        val name = body.substringBefore(' ')
        return name.equals("yes", ignoreCase = true)
    }
}
