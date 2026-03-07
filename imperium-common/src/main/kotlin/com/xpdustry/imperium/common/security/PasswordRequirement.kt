// SPDX-License-Identifier: GPL-3.0-only
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
