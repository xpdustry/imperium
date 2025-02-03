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
@file:Suppress("FunctionName")

package com.xpdustry.imperium.mindustry.account

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.WindowManager
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.mindustry.gui.TextFormWindowManager
import com.xpdustry.imperium.mindustry.misc.CoroutineAction
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.key
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.translation.SCARLET

val REMEMBER_LOGIN_WARNING = key("remember-login-warning", Boolean::class.javaObjectType)

fun LoginWindow(plugin: MindustryPlugin, accounts: AccountManager): WindowManager =
    TextFormWindowManager<LoginPage>(
        plugin,
        "login",
        footer = true,
        extra = { ctx ->
            if (ctx.state[REMEMBER_LOGIN_WARNING] != true) return@TextFormWindowManager
            ctx.pane.description =
                components(ctx.pane.description, newline(), newline(), gui_login_warning_no_remember_login())
        },
        submit =
            BiAction.delegate { _, data ->
                CoroutineAction(success = LoginResultAction()) { window ->
                    accounts.login(
                        window.viewer.sessionKey,
                        data[LoginPage.USERNAME]!!,
                        data[LoginPage.PASSWORD]!!.toCharArray(),
                    )
                }
            },
    )

private enum class LoginPage {
    USERNAME,
    PASSWORD,
}

private fun LoginResultAction() =
    BiAction<AccountResult> { window, result ->
        when (result) {
            is AccountResult.Success -> {
                window.viewer.asAudience.sendAnnouncement(gui_login_success())
                Distributor.get().eventBus.post(PlayerLoginEvent(window.viewer))
            }
            AccountResult.WrongPassword,
            AccountResult.NotFound -> {
                window.show()
                window.viewer.asAudience.sendAnnouncement(gui_login_failure_invalid_credentials())
            }
            else -> handleAccountResult(result, window)
        }
    }

private fun gui_login_success(): Component = translatable("imperium.gui.login.success", ComponentColor.GREEN)

private fun gui_login_warning_no_remember_login(): Component =
    translatable("imperium.gui.login.warning.no_remember_login", SCARLET)

private fun gui_login_failure_invalid_credentials(): Component =
    translatable("imperium.gui.login.failure.invalid-credentials", SCARLET)
