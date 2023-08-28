/*
 * Imperium, the software collection powering the Xpdustry network.
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
package com.xpdustry.imperium.discord.interaction.command

import com.xpdustry.imperium.discord.interaction.Permission

// TODO Rename like InteractionButton ?
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Command(vararg val path: String, val permission: Permission = Permission.EVERYONE, val ephemeral: Boolean = true)

val Command.name: String
    get() = path[0]

fun Command.validate() {
    if (path.isEmpty()) {
        throw IllegalArgumentException("Command name cannot be empty")
    }
    if (path.any { !it.matches(Regex("^[a-zA-Z][a-zA-Z0-9]*$")) }) {
        throw IllegalArgumentException("Command name must be alphanumeric and start with a letter")
    }
}
