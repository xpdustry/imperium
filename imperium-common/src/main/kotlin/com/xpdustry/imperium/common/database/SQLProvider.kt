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
package com.xpdustry.imperium.common.database

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.SQLiteDialect

interface SQLProvider {
    fun <T> newTransaction(block: () -> T): T

    suspend fun <T> newSuspendTransaction(block: suspend () -> T): T
}

class SimpleSQLProvider(private val config: DatabaseConfig.SQL) :
    SQLProvider, ImperiumApplication.Listener {

    private lateinit var database: Database
    private lateinit var handle: Handle

    override fun onImperiumInit() {
        handle =
            when (config.type) {
                DatabaseConfig.SQL.Type.SQLITE -> SQLiteHandle()
                DatabaseConfig.SQL.Type.MARIADB -> MariadbHandle()
            }

        database = handle.start()
        TransactionManager.defaultDatabase = null
        unregisterDriver(config.type.driver)
    }

    override fun onImperiumExit() {
        handle.close()
    }

    override fun <T> newTransaction(block: () -> T): T = transaction { block() }

    override suspend fun <T> newSuspendTransaction(block: suspend () -> T): T =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) { block() }

    private fun unregisterDriver(name: String) {
        // Calling Class.forName("com.mysql.cj.jdbc.Driver") is enough to call the static
        // initializer
        // which makes our driver available in DriverManager. We don't want that, so unregister it
        // after
        // the pool has been set up.
        val drivers = DriverManager.getDrivers()
        while (drivers.hasMoreElements()) {
            val driver = drivers.nextElement()
            if (driver.javaClass.getName() == name) {
                try {
                    DriverManager.deregisterDriver(driver)
                } catch (_: SQLException) {}
            }
        }
    }

    // TODO Goofy name, but meh, who cares at this point
    private interface Handle {
        fun start(): Database

        fun close()
    }

    inner class SQLiteHandle : Handle {

        private lateinit var connection: NonCloseableConnection

        override fun start(): Database {
            val constructor =
                javaClass.classLoader
                    .loadClass("org.sqlite.jdbc4.JDBC4Connection")
                    .getConstructor(String::class.java, String::class.java, Properties::class.java)
            connection =
                NonCloseableConnection(
                    constructor.newInstance("jdbc:sqlite:${config.host}", config.host, Properties())
                        as Connection)
            val config =
                org.jetbrains.exposed.sql.DatabaseConfig.invoke {
                    explicitDialect = SQLiteDialect()
                }
            return Database.connect({ connection }, config)
        }

        override fun close() {
            connection.close0()
        }
    }

    inner class MariadbHandle : Handle {

        private lateinit var source: HikariDataSource

        override fun start(): Database {
            val hikari = HikariConfig()
            hikari.poolName = "imperium-sql-pool"
            hikari.minimumIdle = config.poolMin
            hikari.maximumPoolSize = config.poolMax
            hikari.driverClassName = DatabaseConfig.SQL.Type.MARIADB.driver
            hikari.jdbcUrl = "jdbc:mariadb://${config.host}:${config.port}/${config.database}"
            hikari.username = config.username
            hikari.password = config.password.value
            source = HikariDataSource(hikari)
            return Database.connect(source)
        }

        override fun close() {
            source.close()
        }
    }
}
