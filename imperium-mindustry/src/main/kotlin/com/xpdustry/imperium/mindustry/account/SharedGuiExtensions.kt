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

// TODO Replace "It" by an argument which is either "username" or "password"
private fun getErrorMessage(requirement: StringRequirement) =
    when (requirement) {
        is Letter ->
            when (requirement) {
                Letter.HAS_LOWERCASE -> "It needs at least one lowercase letter."
                Letter.HAS_UPPERCASE -> "It needs at least one uppercase letter."
                Letter.ALL_LOWERCASE -> "Uppercase letters aren't allowed."
                Letter.HAS_DIGIT -> "It needs at least a number."
                Letter.HAS_SPACIAL_SYMBOL -> "It needs at least a symbol."
            }
        is StringRequirement.AllowedSpecialSymbol ->
            "It can only contain the following special symbols: ${requirement.allowed.joinToString()}."
        is StringRequirement.Length ->
            "It needs to be between ${requirement.min} and ${requirement.max} characters long."
        is StringRequirement.Reserved -> "It's is reserved or already taken, try something else."
    }
