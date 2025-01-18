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
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.WindowManager
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.mindustry.gui.TextFormWindowManager
import com.xpdustry.imperium.mindustry.misc.CoroutineAction
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.translation.gui_failure_password_mismatch

fun ChangePasswordWindow(plugin: MindustryPlugin, accounts: AccountManager): WindowManager =
    TextFormWindowManager<ChangePwdPage>(
        plugin,
        "change-password",
        submit =
            BiAction.delegate { _, data ->
                if (data[ChangePwdPage.NEW_PASSWORD]!! != data[ChangePwdPage.CONFIRM_NEW_PASSWORD]!!) {
                    return@delegate Action(Window::show)
                        .then(Action.audience { it.sendAnnouncement(gui_failure_password_mismatch()) })
                }
                CoroutineAction(success = ChangePasswordResultAction()) { window ->
                    val account =
                        accounts.selectBySession(window.viewer.sessionKey)
                            ?: return@CoroutineAction AccountResult.NotFound
                    accounts.updatePassword(
                        account.id,
                        data[ChangePwdPage.OLD_PASSWORD]!!.toCharArray(),
                        data[ChangePwdPage.NEW_PASSWORD]!!.toCharArray(),
                    )
                }
            },
    )

private enum class ChangePwdPage {
    OLD_PASSWORD,
    NEW_PASSWORD,
    CONFIRM_NEW_PASSWORD,
}

private fun ChangePasswordResultAction() =
    BiAction<AccountResult> { window, result ->
        when (result) {
            is AccountResult.Success -> window.viewer.asAudience.sendAnnouncement(gui_change_password_success())
            else -> handleAccountResult(result, window)
        }
    }

private fun gui_change_password_success(): Component =
    translatable("imperium.gui.change-password.success", ComponentColor.GREEN)
