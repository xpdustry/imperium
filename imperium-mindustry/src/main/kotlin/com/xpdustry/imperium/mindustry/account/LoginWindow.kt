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

import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.WindowManager
import com.xpdustry.distributor.api.gui.input.TextInputManager
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.translation.TranslationArguments
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.mindustry.misc.CoroutineAction
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.key
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.translation.GRAY
import com.xpdustry.imperium.mindustry.translation.SCARLET

fun LoginWindow(plugin: MindustryPlugin, accounts: AccountManager): WindowManager {
    val input = TextInputManager.create(plugin)
    input.addTransformer { (pane, state) ->
        val page = state[PAGE_KEY] ?: LoginPage.USERNAME
        pane.title = gui_login_title(page.ordinal + 1, LoginPage.entries.size)
        pane.description = gui_login_page_description(page)
        pane.maxLength = 64

        val action =
            when (page) {
                LoginPage.USERNAME ->
                    BiAction.with(USERNAME_KEY).then(Action.with(PAGE_KEY, LoginPage.PASSWORD)).then(Window::show)
                LoginPage.PASSWORD ->
                    BiAction.delegate { _, password ->
                        CoroutineAction(success = LoginResultAction()) { window ->
                            accounts.login(
                                window.viewer.sessionKey,
                                window.state[USERNAME_KEY]!!,
                                password.toCharArray(),
                            )
                        }
                    }
            }

        pane.inputAction = BiAction.from<String>(Window::hide).then(action)

        pane.exitAction =
            when (page) {
                LoginPage.USERNAME -> Action.back()
                LoginPage.PASSWORD -> Action.with(PAGE_KEY, LoginPage.USERNAME).then(Window::show)
            }
    }
    return input
}

private enum class LoginPage {
    USERNAME,
    PASSWORD,
}

private val PAGE_KEY = key<LoginPage>("page")
private val USERNAME_KEY = key<String>("username")

private fun LoginResultAction() =
    BiAction<AccountResult> { window, result ->
        when (result) {
            is AccountResult.Success -> window.viewer.asAudience.sendAnnouncement(gui_login_success())
            AccountResult.WrongPassword,
            AccountResult.NotFound -> {
                window.show()
                window.viewer.asAudience.sendAnnouncement(gui_login_failure_invalid_credentials())
            }
            else -> handleAccountResult(result, window)
        }
    }

private fun gui_login_title(page: Int, pages: Int): Component =
    translatable("imperium.gui.login.title", TranslationArguments.array(page, pages))

private fun gui_login_page_description(page: LoginPage): Component =
    components(
        translatable("imperium.gui.login.page.${page.name.lowercase()}.description"),
        newline(),
        newline(),
        translatable("imperium.gui.login.footer", GRAY),
    )

private fun gui_login_success(): Component = translatable("imperium.gui.login.success", ComponentColor.GREEN)

private fun gui_login_failure_invalid_credentials(): Component =
    translatable("imperium.gui.login.failure.invalid-credentials", SCARLET)
