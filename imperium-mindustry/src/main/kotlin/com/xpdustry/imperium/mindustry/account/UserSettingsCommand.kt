// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.CoroutineAction
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.key
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.store.DataStoreService
import com.xpdustry.imperium.mindustry.translation.gui_close
import com.xpdustry.imperium.mindustry.translation.gui_user_settings_description
import com.xpdustry.imperium.mindustry.translation.gui_user_settings_entry
import com.xpdustry.imperium.mindustry.translation.gui_user_settings_title
import com.xpdustry.imperium.mindustry.translation.user_setting_description
import mindustry.gen.Player

@Inject
class UserSettingsCommand(
    private val users: UserManager,
    private val store: DataStoreService,
    plugin: MindustryPlugin,
) : ImperiumApplication.Listener {
    private val playerSettingsInterface = createPlayerSettingsInterface(plugin)

    @ImperiumCommand(["settings"])
    @ClientSide
    suspend fun onUserSettingsCommand(sender: CommandSender) {
        val settings = loadUserSettings(sender.player)
        runMindustryThread {
            val window = playerSettingsInterface.create(sender.player)
            window.state[SETTINGS] = settings
            window.show()
        }
    }

    private fun createPlayerSettingsInterface(plugin: MindustryPlugin): MenuManager =
        MenuManager.create(plugin).apply {
            addTransformer { (pane, state) ->
                pane.title = gui_user_settings_title()
                pane.content = gui_user_settings_description()
                state[SETTINGS]!!
                    .entries
                    .sortedBy { it.key.name }
                    .forEach { (setting, value) ->
                        pane.grid.addRow(
                            MenuOption.of(
                                gui_user_settings_entry(setting, value),
                                CoroutineAction(
                                    success =
                                        BiAction.from(
                                            Action.compute(SETTINGS) { it + (setting to !value) }.then(Window::show)
                                        )
                                ) {
                                    users.setSetting(it.viewer.uuid(), setting, !value)
                                },
                            )
                        )
                        pane.grid.addRow(MenuOption.of(user_setting_description(setting), Action.none()))
                    }
                pane.grid.addRow(MenuOption.of(gui_close(), Action.back()))
            }
        }

    private suspend fun loadUserSettings(player: Player): Map<User.Setting, Boolean> {
        val settings = users.getSettings(player.uuid()).toMutableMap()
        for (setting in User.Setting.entries) settings.putIfAbsent(setting, setting.default)
        val achievements = store.selectBySessionKey(player.sessionKey)?.achievements.orEmpty()
        settings.keys.removeAll { it.deprecated || (it.achievement != null && it.achievement !in achievements) }
        return settings
    }

    companion object {
        private val SETTINGS = key<Map<User.Setting, Boolean>>("settings")
    }
}
