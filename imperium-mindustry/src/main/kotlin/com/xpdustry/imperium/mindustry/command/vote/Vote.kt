// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.command.vote

enum class Vote {
    YES,
    NO;

    fun asBoolean() = this == YES

    fun asInt(): Int = if (this == YES) 1 else -1

    companion object {
        operator fun invoke(boolean: Boolean) = if (boolean) YES else NO
    }
}
