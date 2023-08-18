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
package com.xpdustry.imperium.common.message

import com.xpdustry.imperium.common.application.ImperiumMetadata
import com.xpdustry.imperium.common.application.SimpleImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.commonModule
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.MessengerConfig
import com.xpdustry.imperium.common.inject.factory
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.module
import com.xpdustry.imperium.common.inject.single
import com.xpdustry.imperium.common.misc.ExitStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class RabbitmqMessengerTest {
    private lateinit var application: SimpleImperiumApplication
    private lateinit var messenger1: RabbitmqMessenger
    private lateinit var messenger2: RabbitmqMessenger

    @BeforeEach
    fun init() {
        application = SimpleImperiumApplication(MODULE)
        messenger1 = application.instances.get<Messenger>() as RabbitmqMessenger
        messenger2 = application.instances.get<Messenger>() as RabbitmqMessenger
        application.init()
    }

    @AfterEach
    fun exit() {
        application.exit(ExitStatus.EXIT)
    }

    @Test
    fun test_simple_pubsub() = runTest {
        val message = TestMessage("Hello World!")
        val deferred = CompletableDeferred<TestMessage>()
        messenger1.subscribe<TestMessage> {
            deferred.complete(it)
        }
        Assertions.assertTrue(messenger2.publish(message))
        val result = withContext(ImperiumScope.MAIN.coroutineContext) {
            withTimeout(3.seconds) {
                deferred.await()
            }
        }
        Assertions.assertEquals(message, result)
    }

    data class TestMessage(val content: String) : Message

    companion object {
        @Container
        private val RABBITMQ_CONTAINER = RabbitMQContainer(DockerImageName.parse("rabbitmq:3"))
        private val MODULE = module("rabbitmq-messenger-test") {
            include(commonModule())
            single<ImperiumConfig> {
                ImperiumConfig(messenger = MessengerConfig.RabbitMQ(port = RABBITMQ_CONTAINER.amqpPort))
            }
            factory<Messenger> {
                RabbitmqMessenger(get(), ImperiumMetadata())
            }
        }
    }
}
