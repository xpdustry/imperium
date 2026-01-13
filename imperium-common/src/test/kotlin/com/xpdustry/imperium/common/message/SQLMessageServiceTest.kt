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
package com.xpdustry.imperium.common.message

import com.sksamuel.hoplite.Secret
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.database.SQLDatabaseImpl
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mariadb.MariaDBContainer

@Testcontainers
class SQLMessageServiceTest {
    @TempDir private lateinit var tempDir: Path

    private lateinit var database: SQLDatabaseImpl
    private val config1 = ImperiumConfig(server = ServerConfig("test1"))
    private val config2 = ImperiumConfig(server = ServerConfig("test2"))
    private lateinit var service1: SQLMessageService
    private lateinit var service2: SQLMessageService

    @BeforeEach
    fun init() {
        database =
            SQLDatabaseImpl(
                ImperiumConfig(
                    database =
                        DatabaseConfig.MariaDB(
                            host = MARIADB.host,
                            port = MARIADB.firstMappedPort,
                            username = MARIADB.username,
                            database = MARIADB.databaseName,
                            password = Secret(MARIADB.password),
                        )
                ),
                tempDir,
            )
        database.onImperiumInit()
        service1 = SQLMessageService(database, config1, ImperiumScope.MAIN)
        service2 = SQLMessageService(database, config2, ImperiumScope.MAIN)
        service1.onImperiumInit()
        service2.onImperiumInit()
    }

    @AfterEach
    fun exit() {
        service1.onImperiumExit()
        service2.onImperiumExit()
        database.onImperiumExit()
    }

    @Test
    fun `test pubsub simple`() = runBlocking {
        val message = TestMessage("Hello World!")
        val deferred = CompletableDeferred<TestMessage>()

        service1.subscribe<TestMessage> {
            if (!deferred.complete(it)) {
                Assertions.fail("Received message $it twice")
            }
        }

        service2.broadcast(message)
        val result = withTimeout(5.seconds) { deferred.await() }
        Assertions.assertEquals(message, result)
    }

    @Test
    fun `test pubsub local`() = runBlocking {
        val message = TestMessage("Hello World!")
        val deferred1 = CompletableDeferred<TestMessage>()
        val deferred2 = CompletableDeferred<TestMessage>()

        service1.subscribe<TestMessage> {
            if (!deferred1.complete(it)) {
                Assertions.fail("Received message $it twice")
            }
        }
        service2.subscribe<TestMessage> {
            if (!deferred2.complete(it)) {
                Assertions.fail("Received message $it twice")
            }
        }

        service1.broadcast(message, local = true)

        val result1 = withTimeout(3.seconds) { deferred1.await() }
        Assertions.assertEquals(message, result1)

        val result2 = withTimeout(3.seconds) { deferred2.await() }
        Assertions.assertEquals(message, result2)
    }

    @Test
    fun `test cancel`() = runBlocking {
        val set = CopyOnWriteArraySet<String>()
        val handle1 = service1.subscribe<TestMessage> { set += "A${it.content}" }
        val handle2 = service1.subscribe<TestMessage> { set += "B${it.content}" }

        service2.broadcast(TestMessage("1"))
        delay(1.seconds)
        handle1.cancel()

        service2.broadcast(TestMessage("2"))
        delay(1.seconds)
        handle2.cancel()

        service2.broadcast(TestMessage("3"))
        delay(1.seconds)

        Assertions.assertEquals(setOf("A1", "B1", "B2"), set)
        Assertions.assertEquals(0, service1.subscribers.subscriptionCount.value)
    }

    @Serializable data class TestMessage(val content: String) : Message

    companion object {
        @Container private val MARIADB = MariaDBContainer("mariadb:latest")
    }
}
