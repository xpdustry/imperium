// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.string.StringRequirement

sealed interface AccountResult {

    data object Success : AccountResult

    data object AlreadyRegistered : AccountResult

    data object NotFound : AccountResult

    data object AlreadyLogged : AccountResult

    data object WrongPassword : AccountResult

    data class InvalidPassword(val missing: List<StringRequirement>) : AccountResult

    data class InvalidUsername(val missing: List<StringRequirement>) : AccountResult
}
