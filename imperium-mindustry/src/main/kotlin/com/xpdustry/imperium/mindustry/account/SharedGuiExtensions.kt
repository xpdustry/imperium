// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.string.StringRequirement
import com.xpdustry.imperium.common.string.StringRequirement.Letter
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
                "The password does not meet the requirements:\n ${result.missing.joinToString("\n- ", transform = ::getErrorMessage)}"
            is AccountResult.InvalidUsername ->
                "The username does not meet the requirements:\n ${result.missing.joinToString("\n- ", transform = ::getErrorMessage)}"
            is AccountResult.AlreadyLogged -> "You are already logged in."
        }
    window?.show()
    player.showInfoMessage("[red]$message")
}

private fun getErrorMessage(requirement: StringRequirement) =
    when (requirement) {
        Letter.HAS_LOWERCASE -> "It needs at least a lowercase letter."
        Letter.HAS_UPPERCASE -> "It needs at least a uppercase letter."
        Letter.HAS_DIGIT -> "It needs at least a number."
        Letter.HAS_SPACIAL_SYMBOL -> "It needs at least a symbol."
        is StringRequirement.AllowedSpecialSymbol ->
            "It can only contain letters, numbers and ${requirement.allowed.joinToString()}."
        is StringRequirement.Length ->
            "It needs to be between ${requirement.min} and ${requirement.max} characters long."
        is StringRequirement.Reserved -> "This username is reserved or already taken."
        Letter.ALL_LOWERCASE -> "Uppercase letters aren't allowed in the username."
    }
