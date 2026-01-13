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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Path
import java.sql.*
import java.time.Instant
import java.util.*
import java.util.regex.Pattern
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

// TODO Add parent job to wait for transaction completion on close...
internal class SQLDatabaseImpl(private val config: ImperiumConfig, private val directory: Path) :
    SQLDatabase, ImperiumApplication.Listener {
    private lateinit var source: HikariDataSource

    override fun onImperiumInit() {
        val hikari = HikariConfig()

        hikari.poolName = "imperium-sql-pool-v2"
        hikari.maximumPoolSize = 8
        hikari.minimumIdle = 2
        hikari.dataSourceProperties["createDatabaseIfNotExist"] = "true"

        when (val database = this.config.database) {
            is DatabaseConfig.MariaDB -> {
                hikari.driverClassName = "org.mariadb.jdbc.Driver"
                hikari.jdbcUrl = "jdbc:mariadb://" + database.host + ":" + database.port + "/" + database.database
                hikari.username = database.username
                hikari.password = database.password.value
            }
            is DatabaseConfig.H2 -> {
                hikari.driverClassName = "org.h2.Driver"
                hikari.jdbcUrl =
                    "jdbc:h2:file:${directory.resolve("${database.database}.h2").toAbsolutePath()};MODE=MYSQL;AUTO_SERVER=TRUE"
            }
        }

        source = HikariDataSource(hikari)
    }

    override fun onImperiumExit() {
        source.close()
    }

    override suspend fun <R> transaction(block: suspend SQLDatabase.Handle.() -> R): R {
        val element = currentCoroutineContext()[CoroutineHandleElement]
        if (element != null && element.config == config) {
            return block(element.handle)
        }
        return this.source.connection.use { connection ->
            val handle = HandleImpl(connection)
            try {
                handle.connection.autoCommit = false
                handle.connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                val result = withContext(CoroutineHandleElement(handle, config.database)) { block(handle) }
                handle.connection.commit()
                return@use result
            } catch (e: SQLException) {
                handle.connection.rollback()
                throw e
            }
        }
    }
}

private class PreparedStatementBuilderImpl(private val raw: String, private val statement: PreparedStatement) :
    SQLDatabase.PreparedStatementBuilder {
    private var index = 1

    override fun push(value: String): SQLDatabase.PreparedStatementBuilder {
        statement.setString(index, value)
        index++
        return this
    }

    override fun push(value: Int): SQLDatabase.PreparedStatementBuilder {
        statement.setInt(index, value)
        index++
        return this
    }

    override fun push(value: Long): SQLDatabase.PreparedStatementBuilder {
        statement.setLong(index, value)
        index++
        return this
    }

    override fun push(value: Boolean): SQLDatabase.PreparedStatementBuilder {
        statement.setBoolean(index, value)
        index++
        return this
    }

    override fun push(value: Float): SQLDatabase.PreparedStatementBuilder {
        statement.setFloat(index, value)
        index++
        return this
    }

    override fun push(value: ByteArray): SQLDatabase.PreparedStatementBuilder {
        statement.setBytes(index, value)
        index++
        return this
    }

    override fun push(value: Instant): SQLDatabase.PreparedStatementBuilder {
        statement.setTimestamp(index, Timestamp.from(value))
        index++
        return this
    }

    override suspend fun <T> executeSelect(mapper: SQLDatabase.Row.() -> T): MutableList<T> {
        statement.use { statement ->
            return statement.executeQuery().use { result ->
                val view = RowImpl(result)
                val list = ArrayList<T>()
                while (result.next()) list += view.mapper()
                return@use list
            }
        }
    }

    override suspend fun executeUpdate(): Int {
        statement.use { statement ->
            var result = statement.executeUpdate()
            if (result == 2 && INSERT_WITH_UPDATE_PATTERN.matcher(raw).find()) {
                result--
            }
            return result
        }
    }

    override suspend fun executeSingleUpdate() =
        when (val result = this.executeUpdate()) {
            0 -> false
            1 -> true
            else -> error("Multiple rows updated, expected 0 or 1, got $result")
        }

    companion object {
        // MySQL/MariaDB handles this as a delete + insert in case of a duplicate, 2 operations therefore 2 updates
        private val INSERT_WITH_UPDATE_PATTERN =
            Pattern.compile("^\\s*INSERT\\s+(.|\\s)*\\s+ON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\s+", Pattern.CASE_INSENSITIVE)
    }
}

private data class HandleImpl(val connection: Connection) : SQLDatabase.Handle {
    override fun String.asPreparedStatement() = PreparedStatementBuilderImpl(this, connection.prepareStatement(this))
}

private class RowImpl(private val set: ResultSet) : SQLDatabase.Row {
    override fun getString(name: String): String? = set.getString(name)

    override fun getInt(name: String): Int? {
        val value = set.getInt(name)
        return if (set.wasNull()) null else value
    }

    override fun getLong(name: String): Long? {
        val value = set.getLong(name)
        return if (set.wasNull()) null else value
    }

    override fun getBoolean(name: String): Boolean? {
        val value = set.getBoolean(name)
        return if (set.wasNull()) null else value
    }

    override fun getFloat(name: String): Float? {
        val value = set.getFloat(name)
        return if (set.wasNull()) null else value
    }

    override fun getBytes(name: String): ByteArray? = set.getBytes(name)

    override fun getInstant(name: String): Instant? = set.getTimestamp(name).toInstant()
}

private class CoroutineHandleElement(val handle: HandleImpl, val config: DatabaseConfig) :
    AbstractCoroutineContextElement(CoroutineHandleElement) {
    companion object Key : CoroutineContext.Key<CoroutineHandleElement>
}
