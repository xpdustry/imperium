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

sealed interface UsernameRequirement {
    fun check(username: String): Boolean

    data object AllLowercase : UsernameRequirement {
        override fun check(username: String): Boolean = username.lowercase() == username
    }

    data class InvalidSymbol(val allowed: Set<Char>) : UsernameRequirement {
        override fun check(username: String) = username.all { it.isLetterOrDigit() || it in allowed }
    }

    data class Length(val min: Int, val max: Int) : UsernameRequirement {
        override fun check(username: String) = username.length in min..max
    }

    data class Reserved(val reserved: Set<String>) : UsernameRequirement {
        constructor(vararg reserved: String) : this(reserved.toSet())

        override fun check(username: String) = username !in reserved
    }
}

fun List<UsernameRequirement>.findMissingUsernameRequirements(username: String): List<UsernameRequirement> {
    return filter { !it.check(username) }
}

val DEFAULT_USERNAME_REQUIREMENTS =
    listOf(
        UsernameRequirement.InvalidSymbol(allowed = setOf('_')),
        UsernameRequirement.AllLowercase,
        UsernameRequirement.Length(3, 32),
        UsernameRequirement.Reserved(
            UsernameRequirement::class
                .java
                .getResourceAsStream("/reserved-usernames.txt")!!
                .bufferedReader()
                .use { it.readLines() }
                .filter { it.isNotBlank() && !it.startsWith('#') }
                .toSet()
        ),
    )
