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
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.security.PasswordRequirement
import com.xpdustry.imperium.common.security.UsernameRequirement

sealed interface AccountResult {

    data object Success : AccountResult

    data object AlreadyRegistered : AccountResult

    data object NotFound : AccountResult

    data object AlreadyLogged : AccountResult

    data object WrongPassword : AccountResult

    data class InvalidPassword(val missing: List<PasswordRequirement>) : AccountResult

    data class InvalidUsername(val missing: List<UsernameRequirement>) : AccountResult
}
