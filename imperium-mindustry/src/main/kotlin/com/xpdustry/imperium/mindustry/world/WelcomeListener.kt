// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.WindowManager
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.DISCORD_INVITATION_LINK
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.translation.gui_close
import com.xpdustry.imperium.mindustry.translation.gui_rules_content
import com.xpdustry.imperium.mindustry.translation.gui_rules_title
import com.xpdustry.imperium.mindustry.translation.gui_welcome_button_changelog
import com.xpdustry.imperium.mindustry.translation.gui_welcome_button_discord
import com.xpdustry.imperium.mindustry.translation.gui_welcome_button_rules
import com.xpdustry.imperium.mindustry.translation.gui_welcome_content
import com.xpdustry.imperium.mindustry.translation.gui_welcome_title
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Call

@Inject
class WelcomeListener constructor(private val users: UserManager, plugin: MindustryPlugin) :
    ImperiumApplication.Listener {
    private val rulesInterface: WindowManager
    private val welcomeInterface: WindowManager

    init {
        rulesInterface = MenuManager.create(plugin)
        rulesInterface.addTransformer { (pane) ->
            pane.title = gui_rules_title()
            pane.content = gui_rules_content()
            pane.grid.addRow(MenuOption.of(gui_close(), Action.back()))
        }

        welcomeInterface = MenuManager.create(plugin)
        welcomeInterface.addTransformer { (pane) ->
            pane.title = gui_welcome_title()
            pane.content = gui_welcome_content()
            pane.grid.addRow(MenuOption.of(gui_welcome_button_rules(), Action.show(rulesInterface)))
            pane.grid.addRow(
                MenuOption.of(
                    gui_welcome_button_discord(),
                    Action.audience { it.openURI(DISCORD_INVITATION_LINK.toURI()) },
                )
            )
            pane.grid.addRow(MenuOption.of(gui_welcome_button_changelog(), Action.command("changelog")))
            pane.grid.addRow(MenuOption.of(gui_close(), Window::hide))
        }
    }

    @ImperiumCommand(["rules"])
    @ClientSide
    fun onWelcomeCommand(sender: CommandSender) {
        rulesInterface.create(sender.player).show()
    }

    @ImperiumCommand(["discord"])
    @ClientSide
    fun onDiscordCommand(sender: CommandSender) {
        Call.openURI(sender.player.con, DISCORD_INVITATION_LINK.toString())
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        ImperiumScope.MAIN.launch {
            if (users.getSetting(event.player.uuid(), User.Setting.SHOW_WELCOME_MESSAGE)) {
                runMindustryThread { welcomeInterface.create(event.player).show() }
            }
        }
    }
}
