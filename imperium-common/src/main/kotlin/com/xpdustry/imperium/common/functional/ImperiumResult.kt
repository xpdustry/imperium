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
package com.xpdustry.imperium.common.functional

sealed interface ImperiumResult<V : Any, E : Any> {
    val value: V?
    val error: E?

    data class Success<V : Any, E : Any>(override val value: V) : ImperiumResult<V, E> {
        override val error: E? = null
    }

    data class Failure<V : Any, E : Any>(override val error: E) : ImperiumResult<V, E> {
        override val value: V? = null
    }

    companion object {
        fun <V : Any, E : Any> success(value: V): ImperiumResult<V, E> = Success(value)

        fun <V : Any, E : Any> failure(error: E): ImperiumResult<V, E> = Failure(error)
    }
}
