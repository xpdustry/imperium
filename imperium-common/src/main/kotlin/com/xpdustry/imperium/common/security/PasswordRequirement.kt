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
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.string.Password

sealed interface PasswordRequirement {
    fun check(password: Password): Boolean

    data object LowercaseLetter : PasswordRequirement {
        override fun check(password: Password) = password.value.any { it.isLowerCase() }
    }

    data object UppercaseLetter : PasswordRequirement {
        override fun check(password: Password) = password.value.any { it.isUpperCase() }
    }

    data object Number : PasswordRequirement {
        override fun check(password: Password) = password.value.any { it.isDigit() }
    }

    data object Symbol : PasswordRequirement {
        override fun check(password: Password) = password.value.any { it.isLetterOrDigit().not() }
    }

    data class Length(val min: Int, val max: Int) : PasswordRequirement {
        override fun check(password: Password) = password.value.length in min..max
    }
}

fun List<PasswordRequirement>.findMissingPasswordRequirements(password: Password): List<PasswordRequirement> {
    return filter { !it.check(password) }
}

val DEFAULT_PASSWORD_REQUIREMENTS =
    listOf(
        PasswordRequirement.Length(8, 64),
        PasswordRequirement.Number,
        PasswordRequirement.Symbol,
        PasswordRequirement.LowercaseLetter,
        PasswordRequirement.UppercaseLetter,
    )
