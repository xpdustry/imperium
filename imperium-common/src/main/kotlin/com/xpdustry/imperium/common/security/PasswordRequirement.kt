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

sealed interface PasswordRequirement {
    fun check(password: CharArray): Boolean

    data object LowercaseLetter : PasswordRequirement {
        override fun check(password: CharArray) = password.any { it.isLowerCase() }
    }

    data object UppercaseLetter : PasswordRequirement {
        override fun check(password: CharArray) = password.any { it.isUpperCase() }
    }

    data object Number : PasswordRequirement {
        override fun check(password: CharArray) = password.any { it.isDigit() }
    }

    data object Symbol : PasswordRequirement {
        override fun check(password: CharArray) = password.any { it.isLetterOrDigit().not() }
    }

    data class Length(val min: Int, val max: Int) : PasswordRequirement {
        override fun check(password: CharArray) = password.size in min..max
    }
}

fun List<PasswordRequirement>.findMissingPasswordRequirements(password: CharArray): List<PasswordRequirement> {
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
