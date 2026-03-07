// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.version

import kotlinx.serialization.Serializable

@Serializable
data class ImperiumVersion(val year: Int, val month: Int, val build: Int) : Comparable<ImperiumVersion> {
    init {
        require(year >= 0) { "Year must be positive" }
        require(month in 1..12) { "Month must be between 1 and 12" }
        require(build >= 0) { "Build must be positive" }
    }

    override fun compareTo(other: ImperiumVersion): Int =
        Comparator.comparing(ImperiumVersion::year)
            .thenComparing(ImperiumVersion::month)
            .thenComparing(ImperiumVersion::build)
            .compare(this, other)

    override fun toString(): String = "$year.$month.$build"

    companion object {
        fun parse(version: String): ImperiumVersion {
            val parts = version.removePrefix("v").removeSuffix("-SNAPSHOT").split(".")
            require(parts.size == 3) { "Version must be in format 'year.month.build'" }
            return ImperiumVersion(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }
    }
}
