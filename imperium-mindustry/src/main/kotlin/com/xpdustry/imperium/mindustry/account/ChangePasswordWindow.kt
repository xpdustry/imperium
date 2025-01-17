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
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
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

fun ChangePasswordWindow(plugin: MindustryPlugin, accounts: AccountManager): WindowManager {
    val input = TextInputManager.create(plugin)
    input.addTransformer { (pane, state) ->
        val page = state[PAGE_KEY] ?: ChangePwdPage.OLD_PWD
        pane.title = gui_change_password_title(page.ordinal + 1, ChangePwdPage.entries.size)
        pane.description = gui_change_password_description(page)
        pane.maxLength = 64

        val action =
            when (page) {
                ChangePwdPage.OLD_PWD ->
                    BiAction.with(OLD_PWD_KEY).then(Action.with(PAGE_KEY, ChangePwdPage.NEW_PWD)).then(Window::show)
                ChangePwdPage.NEW_PWD ->
                    BiAction.with(NEW_PWD_KEY).then(Action.with(PAGE_KEY, ChangePwdPage.CONFIRM)).then(Window::show)
                ChangePwdPage.CONFIRM ->
                    BiAction.delegate { _, confirm ->
                        if (confirm != state[NEW_PWD_KEY]) {
                            return@delegate Action(Window::show)
                                .then(Action.audience { it.sendAnnouncement(gui_change_password_failure_mismatch()) })
                        }
                        CoroutineAction(success = ChangePasswordResultAction()) { window ->
                            val account =
                                accounts.selectBySession(window.viewer.sessionKey)
                                    ?: return@CoroutineAction AccountResult.NotFound
                            accounts.updatePassword(
                                account.id,
                                window.state[OLD_PWD_KEY]!!.toCharArray(),
                                window.state[NEW_PWD_KEY]!!.toCharArray(),
                            )
                        }
                    }
            }

        pane.inputAction = BiAction.from<String>(Window::hide).then(action)

        pane.exitAction =
            when (page) {
                ChangePwdPage.OLD_PWD -> Action.back()
                ChangePwdPage.NEW_PWD -> Action.with(PAGE_KEY, ChangePwdPage.OLD_PWD).then(Window::show)
                ChangePwdPage.CONFIRM -> Action.with(PAGE_KEY, ChangePwdPage.NEW_PWD).then(Window::show)
            }
    }
    return input
}

private enum class ChangePwdPage {
    OLD_PWD,
    NEW_PWD,
    CONFIRM,
}

private val PAGE_KEY = key<ChangePwdPage>("page")
private val OLD_PWD_KEY = key<String>("old-pwd")
private val NEW_PWD_KEY = key<String>("new-pwd")

private fun ChangePasswordResultAction() =
    BiAction<AccountResult> { window, result ->
        when (result) {
            is AccountResult.Success -> window.viewer.asAudience.sendAnnouncement(gui_change_password_success())
            else -> handleAccountResult(result, window)
        }
    }

private fun gui_change_password_title(page: Int, pages: Int): Component =
    translatable("imperium.gui.change-password.title", TranslationArguments.array(page, pages))

private fun gui_change_password_description(page: ChangePwdPage): Component =
    translatable("imperium.gui.change-password.page.${page.name.lowercase()}.description")

private fun gui_change_password_failure_mismatch(): Component =
    translatable("imperium.gui.change-password.failure.mismatch")

private fun gui_change_password_success(): Component = translatable("imperium.gui.change-password.success")
