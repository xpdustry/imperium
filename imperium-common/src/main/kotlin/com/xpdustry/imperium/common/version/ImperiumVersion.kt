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
            val split = (if (version.startsWith("v")) version.substring(1) else version).split(".")
            require(split.size == 3) { "Version must be in format 'year.month.build'" }
            return ImperiumVersion(split[0].toInt(), split[1].toInt(), split[2].toInt())
        }
    }
}
