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
package com.xpdustry.imperium.common.database

import java.time.Instant

interface SQLDatabase {
    suspend fun <R> transaction(block: suspend Handle.() -> R): R

    interface Handle {
        fun String.asPreparedStatement(): PreparedStatementBuilder
    }

    interface PreparedStatementBuilder {
        fun push(value: String): PreparedStatementBuilder

        fun push(value: Int): PreparedStatementBuilder

        fun push(value: Long): PreparedStatementBuilder

        fun push(value: Boolean): PreparedStatementBuilder

        fun push(value: Float): PreparedStatementBuilder

        fun push(value: ByteArray): PreparedStatementBuilder

        fun push(value: Instant): PreparedStatementBuilder

        // TODO pushRow or rename that whole shi...

        suspend fun <T> executeSelect(mapper: Row.() -> T): MutableList<T>

        suspend fun executeUpdate(): Int

        suspend fun executeSingleUpdate(): Boolean
    }

    interface Row {
        fun getString(name: String): String?

        fun getInt(name: String): Int?

        fun getLong(name: String): Long?

        fun getBoolean(name: String): Boolean?

        fun getFloat(name: String): Float?

        fun getBytes(name: String): ByteArray?

        fun getInstant(name: String): Instant?
    }
}
