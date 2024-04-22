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
data class MindustryVersion(val major: Int, val build: Int, val patch: Int, val type: Type) {
    init {
        require(major >= 0) { "Major version must be positive" }
        require(build >= 0) { "Build version must be positive" }
        require(patch >= 0) { "Patch version must be positive" }
    }

    override fun toString(): String =
        "${type.name.lowercase().replace('_', '-')} v$major ${build}${if (patch == 0) "" else ".$patch"}"

    enum class Type {
        OFFICIAL,
        ALPHA,
        BLEEDING_EDGE,
        CUSTOM,
    }
}
