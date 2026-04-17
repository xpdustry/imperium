// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.string

import java.util.Locale

sealed interface StringRequirement {
    fun isSatisfiedBy(string: CharSequence): Boolean

    enum class Letter : StringRequirement {
        HAS_LOWERCASE {
            override fun isSatisfiedBy(string: CharSequence): Boolean = string.any(Char::isLowerCase)
        },
        HAS_UPPERCASE {
            override fun isSatisfiedBy(string: CharSequence): Boolean = string.any(Char::isUpperCase)
        },
        HAS_DIGIT {
            override fun isSatisfiedBy(string: CharSequence): Boolean = string.any(Char::isDigit)
        },
        HAS_SPACIAL_SYMBOL {
            override fun isSatisfiedBy(string: CharSequence): Boolean = string.any { !it.isLetterOrDigit() }
        },
        ALL_LOWERCASE {
            override fun isSatisfiedBy(string: CharSequence): Boolean = string.none(Char::isUpperCase)
        },
    }

    data class AllowedSpecialSymbol(val allowed: Set<Char>) : StringRequirement {
        constructor(vararg allowed: Char) : this(allowed.toSet())

        override fun isSatisfiedBy(string: CharSequence): Boolean =
            string.all { char -> char.isLetterOrDigit() || char in allowed }
    }

    data class Length(val min: Int, val max: Int) : StringRequirement {
        override fun isSatisfiedBy(string: CharSequence): Boolean = string.length in min..max
    }

    data class Reserved(val reserved: StringTrieMap<Boolean>) : StringRequirement {
        override fun isSatisfiedBy(string: CharSequence): Boolean = !reserved.contains(string, partial = false)
    }
}

fun List<StringRequirement>.findMissingRequirements(string: CharSequence): List<StringRequirement> = filterNot {
    it.isSatisfiedBy(string)
}

val DEFAULT_USERNAME_REQUIREMENTS: List<StringRequirement> = buildList {
    val reserved = StringTrieMap.create<Boolean>()
    StringRequirement::class
        .java
        .getResourceAsStream("/com/xpdustry/imperium/common/string/reserved-usernames.txt")!!
        .bufferedReader()
        .useLines { lines ->
            lines
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotBlank() && !it.startsWith('#') }
                .forEach { reserved.put(it, true) }
        }

    add(StringRequirement.Letter.ALL_LOWERCASE)
    add(StringRequirement.AllowedSpecialSymbol('_'))
    add(StringRequirement.Length(3, 32))
    add(StringRequirement.Reserved(reserved))
}

val DEFAULT_PASSWORD_REQUIREMENTS: List<StringRequirement> =
    listOf(
        StringRequirement.Letter.HAS_LOWERCASE,
        StringRequirement.Letter.HAS_UPPERCASE,
        StringRequirement.Letter.HAS_DIGIT,
        StringRequirement.Letter.HAS_SPACIAL_SYMBOL,
        StringRequirement.Length(8, 64),
    )
