// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.database

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

interface SQLProvider {
    fun <T> newTransaction(block: () -> T): T

    suspend fun <T> newSuspendTransaction(block: suspend () -> T): T
}

@Inject
class SimpleSQLProvider(private val config: DatabaseConfig, @Named("directory") private val directory: Path) :
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
        withContext(scope.coroutineContext) { suspendTransaction(database) { block() } }
}
