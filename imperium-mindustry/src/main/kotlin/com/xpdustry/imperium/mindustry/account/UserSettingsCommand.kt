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
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.imperium.common.account.User
import com.xpdustry.imperium.common.account.UserManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import kotlinx.coroutines.launch

class UserSettingsCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val playerSettingsInterface = createPlayerSettingsInterface(instances.get())

    @Command(["settings"])
    @ClientSide
    private suspend fun onUserSettingsCommand(sender: CommandSender) {
        val settings = loadUserSettings(sender.player.uuid())
        runMindustryThread {
            playerSettingsInterface.open(sender.player) { it[SETTINGS] = settings }
        }
    }

    private fun createPlayerSettingsInterface(plugin: MindustryPlugin): Interface {
        val playerSettingsInterface = MenuInterface.create(plugin)
        playerSettingsInterface.addTransformer { view, pane ->
            pane.title = "Player Settings"
            pane.content = "Change your settings by clicking on the corresponding buttons."
            for ((setting, value) in view.state[SETTINGS]!!) {
                val text = buildString {
                    append(setting.name.lowercase().replace("_", "-"))
                    append(": ")
                    append(if (value) "[green]enabled" else "[red]disabled")
                }
                pane.options.addRow(
                    MenuOption(text) { _ ->
                        val settings = view.state[SETTINGS]!!.toMutableMap()
                        settings[setting] = !value
                        ImperiumScope.MAIN.launch {
                            saveUserSettings(view.viewer.uuid(), settings)
                            view.state[SETTINGS] = settings
                            runMindustryThread { view.open() }
                        }
                    })
                pane.options.addRow(MenuOption("[lightgray]${setting.description}"))
            }
            pane.options.addRow(MenuOption("Close", View::back))
        }
        return playerSettingsInterface
    }

    private suspend fun loadUserSettings(uuid: String): Map<User.Setting, Boolean> {
        val user = users.findByUuidOrCreate(uuid)
        val settings = mutableMapOf<User.Setting, Boolean>()
        for (setting in User.Setting.entries) {
            settings[setting] = user.settings[setting.name] ?: setting.default
        }
        return settings
    }

    private suspend fun saveUserSettings(uuid: String, settings: Map<User.Setting, Boolean>) {
        users.updateOrCreateByUuid(uuid) { user ->
            user.settings.clear()
            settings.entries.toMutableList().forEach { user.settings[it.key.name] = it.value }
        }
    }

    companion object {
        private val SETTINGS = stateKey<Map<User.Setting, Boolean>>("imperium:player-settings")
    }
}
