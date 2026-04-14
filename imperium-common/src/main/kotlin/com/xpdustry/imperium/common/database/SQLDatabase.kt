// SPDX-License-Identifier: GPL-3.0-only
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

        fun push(value: Double): PreparedStatementBuilder

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

        fun getDouble(name: String): Double?

        fun getFloat(name: String): Float?

        fun getBytes(name: String): ByteArray?

        fun getInstant(name: String): Instant?
    }
}
