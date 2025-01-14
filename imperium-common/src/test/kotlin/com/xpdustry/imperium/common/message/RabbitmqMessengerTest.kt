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

import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.MessengerConfig
import com.xpdustry.imperium.common.config.ServerConfig
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
class RabbitmqMessengerTest {
    private lateinit var messenger1: RabbitmqMessenger
    private lateinit var messenger2: RabbitmqMessenger

    @BeforeEach
    fun init() {
        messenger1 = createClient()
        messenger2 = createClient()
        messenger1.onImperiumInit()
        messenger2.onImperiumInit()
    }

    @AfterEach
    fun exit() {
        messenger1.onImperiumExit()
        messenger2.onImperiumExit()
    }

    @Test
    fun `test pubsub simple`() = runTest {
        val message = TestMessage("Hello World!")
        val deferred = CompletableDeferred<TestMessage>()

        messenger1.consumer<TestMessage> {
            if (!deferred.complete(it)) {
                Assertions.fail<Unit>("Received message $it twice")
            }
        }

        Assertions.assertTrue(messenger2.publish(message))
        val result = withContext(ImperiumScope.MAIN.coroutineContext) { withTimeout(3.seconds) { deferred.await() } }
        Assertions.assertEquals(message, result)
    }

    @Test
    fun `test pubsub superclasses`() = runTest {
        val message1 = TestSealedMessage.Number(69)
        val message2 = TestSealedMessage.Text("Hello World!")
        val deferred1 = CompletableDeferred<TestSealedMessage.Number>()
        val deferred2 = CompletableDeferred<TestSealedMessage.Text>()

        messenger1.consumer<TestSealedMessage> {
            when (it) {
                is TestSealedMessage.Number -> {
                    if (!deferred1.complete(it)) {
                        Assertions.fail<Unit>("Received message $it twice")
                    }
                }
                is TestSealedMessage.Text -> {
                    if (!deferred2.complete(it)) {
                        Assertions.fail<Unit>("Received message $it twice")
                    }
                }
            }
        }

        Assertions.assertTrue(messenger2.publish(message1))
        val result = withContext(ImperiumScope.MAIN.coroutineContext) { withTimeout(3.seconds) { deferred1.await() } }
        Assertions.assertEquals(message1, result)

        Assertions.assertTrue(messenger2.publish(message2))
        val result2 = withContext(ImperiumScope.MAIN.coroutineContext) { withTimeout(3.seconds) { deferred2.await() } }
        Assertions.assertEquals(message2, result2)
    }

    @Test
    fun `test pubsub local`() = runTest {
        val message = TestMessage("Hello World!")
        val deferred1 = CompletableDeferred<TestMessage>()
        val deferred2 = CompletableDeferred<TestMessage>()

        messenger1.consumer<TestMessage> {
            if (!deferred1.complete(it)) {
                Assertions.fail<Unit>("Received message $it twice")
            }
        }
        messenger2.consumer<TestMessage> {
            if (!deferred2.complete(it)) {
                Assertions.fail<Unit>("Received message $it twice")
            }
        }

        Assertions.assertTrue(messenger1.publish(message, local = true))

        val result1 = withContext(ImperiumScope.MAIN.coroutineContext) { withTimeout(3.seconds) { deferred1.await() } }
        Assertions.assertEquals(message, result1)

        val result2 = withContext(ImperiumScope.MAIN.coroutineContext) { withTimeout(3.seconds) { deferred2.await() } }
        Assertions.assertEquals(message, result2)
    }

    @Test
    fun `test cancel`() = runTest {
        val set = CopyOnWriteArraySet<String>()
        val job1 = messenger1.consumer<TestMessage> { set += "A${it.content}" }
        val job2 = messenger1.consumer<TestMessage> { set += "B${it.content}" }

        withContext(ImperiumScope.MAIN.coroutineContext) {
            messenger2.publish(TestMessage("1"))
            delay(500)
            job1.cancel()

            messenger2.publish(TestMessage("2"))
            delay(500)
            job2.cancel()

            messenger2.publish(TestMessage("3"))
            delay(500)
        }

        Assertions.assertEquals(setOf("A1", "B1", "B2"), set)
        Assertions.assertTrue(messenger1.flows.isEmpty())
    }

    @Test
    fun `test request simple`() = runTest {
        val message = TestMessage("hello")
        messenger1.function<TestMessage, TestMessage> { TestMessage(it.content.reversed()) }
        val result = messenger2.request<TestMessage>(message)
        Assertions.assertEquals(TestMessage("olleh"), result)
    }

    @Test
    fun `test request type not matching`() = runTest {
        val message = TestMessage("hello")
        messenger1.function<TestMessage, TestMessage> { TestMessage(it.content.reversed()) }
        val result = messenger2.request<TestSealedMessage>(message, timeout = 1.seconds)
        Assertions.assertNull(result)
    }

    @Test
    fun `test request timeout`() = runTest {
        val message = TestMessage("hello")
        val deferred = CompletableDeferred<TestMessage>()

        messenger1.function<TestMessage, TestMessage> {
            delay(2.seconds)
            TestMessage(it.content.reversed())
        }
        messenger1.consumer<TestMessage> {
            if (!deferred.complete(it)) {
                Assertions.fail<Unit>("Received message $it twice")
            }
        }

        val result = messenger2.request<TestMessage>(message, timeout = 1.seconds)
        Assertions.assertNull(result)

        val received = withContext(ImperiumScope.MAIN.coroutineContext) { withTimeout(3.seconds) { deferred.await() } }
        Assertions.assertEquals(message, received)
    }

    @Serializable data class TestMessage(val content: String) : Message

    @Serializable
    sealed interface TestSealedMessage : Message {
        @Serializable data class Number(val number: Int) : TestSealedMessage

        @Serializable data class Text(val text: String) : TestSealedMessage
    }

    private fun createClient() =
        RabbitmqMessenger(
            ImperiumConfig(
                server = ServerConfig("test-${UUID.randomUUID()}"),
                messenger = MessengerConfig.RabbitMQ(port = RABBITMQ.amqpPort),
            )
        )

    companion object {
        @Container private val RABBITMQ = RabbitMQContainer(DockerImageName.parse("rabbitmq:3"))
    }
}
