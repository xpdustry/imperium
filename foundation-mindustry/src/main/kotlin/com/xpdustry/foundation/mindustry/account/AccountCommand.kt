/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.mindustry.account

import cloud.commandframework.kotlin.extension.buildAndRegister
import com.google.inject.Inject
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.database.Database
import com.xpdustry.foundation.mindustry.command.FoundationPluginCommandManager
import com.xpdustry.foundation.mindustry.ui.action.BiAction
import com.xpdustry.foundation.mindustry.ui.input.TextInputInterface
import com.xpdustry.foundation.mindustry.ui.state.stateKey
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import jakarta.inject.Named

private val ERROR_MESSAGE = stateKey<String>("error_message")
private val USERNAME = stateKey<String>("username")

class AccountCommand @Inject constructor(
    private val plugin: MindustryPlugin,
    private val database: Database,
    @param:Named("client") private val clientCommandManager: FoundationPluginCommandManager,
) : FoundationListener {
    override fun onFoundationInit() {
        val passwordInterface = TextInputInterface.create(plugin).addTransformer { view, pane ->
            pane.description = "Your password"
            pane.inputAction = BiAction { _, value ->
                if (value.trim().length < 3) {
                    view.state[ERROR_MESSAGE] = "Password cannot be shorter than 3 characters"
                    return@BiAction
                }
            }
        }

        val usernameInterface = TextInputInterface.create(plugin).addTransformer { view, pane ->
            pane.description = "Your username"
            pane.placeholder = view.viewer.plainName()
            pane.inputAction = BiAction { _, value ->
                if (value.trim().length < 3) {
                    view.state[ERROR_MESSAGE] = "Username cannot be shorter than 3 characters"
                    return@BiAction
                }
            }
        }

        clientCommandManager.buildAndRegister("login") {
            commandDescription("Login to your account")
            handler { ctx ->
            }
        }

        clientCommandManager.buildAndRegister("register") {
            commandDescription("Register an account")
        }
    }
}
