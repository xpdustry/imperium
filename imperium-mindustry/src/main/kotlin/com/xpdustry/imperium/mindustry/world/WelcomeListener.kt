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
package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.annotation.EventHandler
import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.BLURPLE
import com.xpdustry.imperium.common.misc.DISCORD_INVITATION_LINK
import com.xpdustry.imperium.common.misc.toHexString
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.security.MindustryRules
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.action.Action
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Iconc
import mindustry.net.Administration.Config

class WelcomeListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val rulesInterface: Interface
    private val welcomeInterface: Interface

    init {
        rulesInterface = MenuInterface.create(instances.get())
        rulesInterface.addTransformer { _, pane ->
            pane.title = "Da Rules"
            pane.content = buildString {
                // TODO Rules are only "don'ts" right now, add some "do's" too
                append(
                    "[scarlet]${Iconc.warning} You may be banned if you do any of the following:")
                for (rule in MindustryRules.entries) {
                    append("\n\n[royal]» [accent]")
                    append(rule.title)
                    append("\n[white]")
                    append(rule.description)
                    append("\n[lightgray]Example: ")
                    append(rule.example)
                }
            }
            pane.options.addRow(MenuOption("Close", View::back))
        }

        welcomeInterface = MenuInterface.create(instances.get())
        welcomeInterface.addTransformer { _, pane ->
            pane.title = "Hello there"
            pane.content =
                """
                Welcome to
                [white]${Config.serverName.string()}
                [white]Have fun on our server network!

                [gray]You can disable this message with the [accent]/settings[] command.
                """
                    .trimIndent()
            pane.options.addRow(MenuOption("${Iconc.bookOpen} Rules", rulesInterface::open))
            pane.options.addRow(
                MenuOption(
                    "[${BLURPLE.toHexString()}]${Iconc.discord} Discord",
                    Action.uri(DISCORD_INVITATION_LINK.toURI())))
            pane.options.addRow(MenuOption("Close", View::close))
        }
    }

    @Command(["rules"])
    @ClientSide
    private fun onWelcomeCommand(sender: CommandSender) {
        rulesInterface.open(sender.player)
    }

    @Command(["discord"])
    @ClientSide
    private fun onDiscordCommand(sender: CommandSender) {
        Call.openURI(sender.player.con, DISCORD_INVITATION_LINK.toString())
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        ImperiumScope.MAIN.launch {
            if (users.getSetting(event.player.uuid(), User.Setting.SHOW_WELCOME_MESSAGE)) {
                welcomeInterface.open(event.player)
            }
        }
    }
}
