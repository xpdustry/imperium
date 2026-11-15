// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.message

import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.database.SQLDatabaseImpl
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SQLMessageServiceTest {
    @TempDir private lateinit var tempDir: Path

    private lateinit var database: SQLDatabaseImpl
    private val scope = CoroutineScope(Dispatchers.Default)
    private val config1 = ImperiumConfig(server = ServerConfig("test1"))
    private val config2 = ImperiumConfig(server = ServerConfig("test2"))
    private lateinit var service1: SQLMessageService
    private lateinit var service2: SQLMessageService

    @BeforeEach
    fun init() {
        database = SQLDatabaseImpl(ImperiumConfig(database = DatabaseConfig.H2()), tempDir)
        database.onImperiumInit()
        service1 = SQLMessageService(database, config1, scope)
        service2 = SQLMessageService(database, config2, scope)
        service1.onImperiumInit()
        service2.onImperiumInit()
    }

    @AfterEach
    fun exit() {
        service1.onImperiumExit()
        service2.onImperiumExit()
        database.onImperiumExit()
        scope.cancel()
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

    @Test
    fun `test slow subscriber does not block other subscribers`() = runBlocking {
        val response = CompletableDeferred<OtherTestMessage>()
        val result = CompletableDeferred<OtherTestMessage?>()

        // A subscriber that waits for another message while handling one, like a request/response flow
        service1.subscribe<TestMessage> { result.complete(withTimeoutOrNull(3.seconds) { response.await() }) }
        service1.subscribe<OtherTestMessage> { response.complete(it) }

        service2.broadcast(TestMessage("request"))
        delay(1.seconds)
        // Unrelated traffic emitted while the subscriber is busy
        service2.broadcast(TestMessage("noise"))
        delay(1.seconds)
        service2.broadcast(OtherTestMessage("response"))

        Assertions.assertEquals(OtherTestMessage("response"), withTimeout(5.seconds) { result.await() })
    }

    @Test
    fun `test blocked subscriber drops oldest messages on overflow`() = runBlocking {
        val bounded = SQLMessageService(database, ImperiumConfig(server = ServerConfig("test3")), scope, 2)
        bounded.onImperiumInit()
        try {
            val started = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            val done = CompletableDeferred<Unit>()
            val received = CopyOnWriteArrayList<String>()
            bounded.subscribe<TestMessage> {
                started.complete(Unit)
                gate.await()
                received += it.content
                if (it.content == "5") done.complete(Unit)
            }

            // Emitting directly on the shared flow, emit returns once every collector took the value
            bounded.subscribers.emit(TestMessage("first"))
            withTimeout(3.seconds) { started.await() }
            // "first" is being processed, the buffer of 2 only keeps the latest 2 of these
            for (i in 1..5) bounded.subscribers.emit(TestMessage("$i"))
            // The forwarder only takes this once it finished buffering "5"
            bounded.subscribers.emit(OtherTestMessage("sync"))
            gate.complete(Unit)
            withTimeout(3.seconds) { done.await() }

            Assertions.assertEquals(listOf("first", "4", "5"), received)
        } finally {
            bounded.onImperiumExit()
        }
    }

    @Test
    fun `test cancel skips buffered messages`() = runBlocking {
        val received = CopyOnWriteArrayList<String>()
        val handle =
            service1.subscribe<TestMessage> {
                received += it.content
                awaitCancellation()
            }

        service2.broadcast(TestMessage("first"))
        delay(1.seconds)
        service2.broadcast(TestMessage("second"))
        service2.broadcast(TestMessage("third"))
        delay(1.seconds)
        handle.cancel()
        delay(500.milliseconds)

        Assertions.assertEquals(listOf("first"), received)
    }

    @Test
    fun `test ordering across polls`() = runBlocking {
        val received = CopyOnWriteArrayList<String>()
        service1.subscribe<TestMessage> {
            delay(50.milliseconds)
            received += it.content
        }

        val expected = (1..20).map(Int::toString)
        for (content in expected) {
            service2.broadcast(TestMessage(content))
            delay(100.milliseconds)
        }
        delay(2.seconds)

        Assertions.assertEquals(expected, received)
    }

    @Test
    fun `test exit stops subscribers`() = runBlocking {
        service1.subscribe<TestMessage> {}
        Assertions.assertEquals(1, service1.subscribers.subscriptionCount.value)
        service1.onImperiumExit()
        Assertions.assertEquals(0, service1.subscribers.subscriptionCount.value)
    }

    @Serializable data class TestMessage(val content: String) : Message

    @Serializable data class OtherTestMessage(val content: String) : Message
}
