// SPDX-License-Identifier: GPL-3.0-only
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
import com.xpdustry.imperium.common.string.Password
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
                        Password(data[ChangePwdPage.OLD_PASSWORD]!!),
                        Password(data[ChangePwdPage.NEW_PASSWORD]!!),
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
