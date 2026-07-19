// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.string

class MissingStringRequirementException(message: String, missing: List<StringRequirement>) : RuntimeException(message) {
    val missing: List<StringRequirement> = missing.toList()
}
