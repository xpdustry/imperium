// SPDX-License-Identifier: GPL-3.0-only
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
