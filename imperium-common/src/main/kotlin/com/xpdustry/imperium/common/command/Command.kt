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
package com.xpdustry.imperium.common.command

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Command(val path: Array<String>, val permission: Permission = Permission.EVERYONE)

private val PATH_ELEMENT_REGEX = Regex("^[a-zA-Z](-?[a-zA-Z0-9])*$")

val Command.name: String get() = path[0]

fun Command.validate(): Result<Unit> = if (path.isEmpty()) {
    Result.failure(IllegalArgumentException("Command name cannot be empty"))
} else if (path.any { !it.matches(PATH_ELEMENT_REGEX) }) {
    Result.failure(IllegalArgumentException("Command name must be alphanumeric and start with a letter"))
} else {
    Result.success(Unit)
}
