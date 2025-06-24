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
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

interface SQLProvider {
    fun <T> newTransaction(block: () -> T): T

    suspend fun <T> newSuspendTransaction(block: suspend () -> T): T
}

class SimpleSQLProvider(private val config: DatabaseConfig, private val directory: Path) :
    SQLProvider, ImperiumApplication.Listener {

    private val parent = SupervisorJob()
    private val scope = CoroutineScope(ImperiumScope.IO.coroutineContext + parent)
    private lateinit var database: Database
    private lateinit var source: HikariDataSource

    override fun onImperiumInit() {
        val hikari = HikariConfig()
        hikari.poolName = "imperium-sql-pool"
        hikari.maximumPoolSize = 8
        hikari.minimumIdle = 2
        hikari.addDataSourceProperty("createDatabaseIfNotExist", "true")

        when (config) {
            is DatabaseConfig.H2 -> {
                hikari.driverClassName = "org.h2.Driver"
                if (config.memory) {
                    hikari.jdbcUrl = "jdbc:h2:mem:${config.database};MODE=MYSQL"
                } else {
                    hikari.jdbcUrl = "jdbc:h2:file:${directory.resolve("database.h2").absolutePathString()};MODE=MYSQL"
                }
            }
            is DatabaseConfig.MariaDB -> {
                hikari.jdbcUrl = "jdbc:mariadb://${config.host}:${config.port}/${config.database}"
                hikari.username = config.username
                hikari.password = config.password.value
            }
        }

        source = HikariDataSource(hikari)
        database = Database.connect(source)
        TransactionManager.defaultDatabase = null
    }

    override fun onImperiumExit() {
        parent.complete()
        runBlocking { parent.join() }
        source.close()
    }

    override fun <T> newTransaction(block: () -> T): T = transaction { block() }

    override suspend fun <T> newSuspendTransaction(block: suspend () -> T): T =
        newSuspendedTransaction(scope.coroutineContext, database) { block() }
}
