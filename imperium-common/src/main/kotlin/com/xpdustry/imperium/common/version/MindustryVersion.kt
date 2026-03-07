// SPDX-License-Identifier: GPL-3.0-only
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
