/*
 * Foundation, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.foundation.common.misc

fun interface PasswordRequirement {
    fun check(password: CharArray): Boolean
}

fun List<PasswordRequirement>.findMissingRequirements(password: CharArray): List<PasswordRequirement> {
    return filter { !it.check(password) }
}

val DEFAULT_PASSWORD_REQUIREMENTS = listOf(
    LengthRequirement(8, 64),
    NumberRequirement,
    SymbolRequirement,
    UppercaseLetterRequirement,
    LowercaseLetterRequirement,
)

object LowercaseLetterRequirement : PasswordRequirement {
    override fun check(password: CharArray) = password.any { it.isLowerCase() }
}

object UppercaseLetterRequirement : PasswordRequirement {
    override fun check(password: CharArray) = password.any { it.isUpperCase() }
}

object NumberRequirement : PasswordRequirement {
    override fun check(password: CharArray) = password.any { it.isDigit() }
}

object SymbolRequirement : PasswordRequirement {
    override fun check(password: CharArray) = password.any { !it.isLetterOrDigit() }
}

data class LengthRequirement(val min: Int, val max: Int) : PasswordRequirement {
    override fun check(password: CharArray) = password.size in min..max
}
