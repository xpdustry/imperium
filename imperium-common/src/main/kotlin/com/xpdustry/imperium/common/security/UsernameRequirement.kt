// SPDX-License-Identifier: GPL-3.0-only
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
                .getResourceAsStream("/com/xpdustry/imperium/common/string/reserved-usernames.txt")!!
                .bufferedReader()
                .use { it.readLines() }
                .filter { it.isNotBlank() && !it.startsWith('#') }
                .toSet()
        ),
    )
