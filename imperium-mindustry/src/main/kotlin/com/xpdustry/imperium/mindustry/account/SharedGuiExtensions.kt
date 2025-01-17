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
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.security.PasswordRequirement
import com.xpdustry.imperium.common.security.UsernameRequirement
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

private fun getErrorMessage(requirement: PasswordRequirement) =
    when (requirement) {
        is PasswordRequirement.LowercaseLetter -> "It needs at least a lowercase letter."
        is PasswordRequirement.UppercaseLetter -> "It needs at least a uppercase letter."
        is PasswordRequirement.Number -> "It needs at least a number."
        is PasswordRequirement.Symbol -> "It needs at least a symbol."
        is PasswordRequirement.Length ->
            "It needs to be between ${requirement.min} and ${requirement.max} characters long."
    }

private fun getErrorMessage(requirement: UsernameRequirement) =
    when (requirement) {
        is UsernameRequirement.InvalidSymbol ->
            "It can only contain letters, numbers and ${requirement.allowed.joinToString()}."
        is UsernameRequirement.Length ->
            "It needs to be between ${requirement.min} and ${requirement.max} characters long."
        is UsernameRequirement.Reserved -> "This username is reserved or already taken."
        is UsernameRequirement.AllLowercase -> "Uppercase letters aren't allowed in the username."
    }
