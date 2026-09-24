// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.string.toErrorMessage
import com.xpdustry.imperium.mindustry.misc.showInfoMessage
import mindustry.gen.Player

fun handleAccountResult(result: AccountResult, window: Window) {
    handleAccountResult(result, window.viewer, window)
}

fun handleAccountResult(result: AccountResult, player: Player) {
    handleAccountResult(result, player, null)
}

private fun handleAccountResult(result: AccountResult, player: Player, window: Window?) {
    val message =
        when (result) {
            is AccountResult.Success -> "Success!"
            is AccountResult.AlreadyRegistered -> "This account is already registered!"
            is AccountResult.NotFound -> "You are not registered!"
            is AccountResult.WrongPassword -> "Wrong password!"
            is AccountResult.InvalidPassword ->
                "The password does not meet the requirements:\n- ${result.missing.joinToString("\n- ") { it.toErrorMessage() }}"
            is AccountResult.InvalidUsername ->
                "The username does not meet the requirements:\n- ${result.missing.joinToString("\n- ") { it.toErrorMessage() }}"
            is AccountResult.AlreadyLogged -> "You are already logged in."
        }
    window?.show()
    player.showInfoMessage("[red]$message")
}
