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

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownSignalException
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.application.ImperiumMetadata
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.MessengerConfig
import com.xpdustry.imperium.common.misc.LoggerDelegate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.jvm.jvmName

class RabbitmqMessenger(private val config: ImperiumConfig, private val metadata: ImperiumMetadata) : Messenger, ImperiumApplication.Listener {
    private val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
    private val flows = ConcurrentHashMap<KClass<out Message>, FlowWithCTag<out Message>>()
    private lateinit var channel: Channel
    private lateinit var connection: Connection

    override fun onImperiumInit() {
        if (config.messenger !is MessengerConfig.RabbitMQ) {
            throw IllegalStateException("The current Messenger configuration is not RabbitMQ")
        }
        val factory = ConnectionFactory().apply {
            host = config.messenger.host
            port = config.messenger.port
            isAutomaticRecoveryEnabled = true

            if (username.isNotBlank()) {
                username = config.messenger.username
                password = config.messenger.password.value
            }

            if (config.messenger.ssl) {
                useSslProtocol(SSLContext.getDefault())
            }
        }

        connection = factory.newConnection(metadata.identifier.toString())
        channel = connection.createChannel()
        channel.exchangeDeclare(IMPERIUM_EXCHANGE, BuiltinExchangeType.DIRECT, false, true, null)
    }

    override fun onImperiumExit() {
        channel.close()
        connection.close()
    }

    override suspend fun <M : Message> publish(message: M, local: Boolean) = withContext(ImperiumScope.IO.coroutineContext) {
        try {
            if (local) {
                handleIncomingMessage(message)
            }
            val bytes = gson.toJson(message).toByteArray()
            forEachMessageSuperclass(message::class) {
                channel.basicPublish(
                    IMPERIUM_EXCHANGE,
                    it.jvmName,
                    AMQP.BasicProperties.Builder()
                        .headers(
                            mapOf(
                                SENDER_HEADER to metadata.identifier.toString(),
                                JAVA_CLASS_HEADER to message::class.jvmName,
                            ),
                        )
                        .build(),
                    bytes,
                )
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to publish ${message::class.simpleName ?: message::class.jvmName} message", e)
            false
        }
    }

    override fun <M : Message> subscribe(type: KClass<M>, listener: Messenger.Listener<M>): Job {
        @Suppress("UNCHECKED_CAST")
        val flow = flows.getOrPut(type) {
            val queue = channel.queueDeclare().queue
            channel.queueBind(queue, IMPERIUM_EXCHANGE, type.jvmName)
            FlowWithCTag(channel.basicConsume(queue, true, RabbitmqAdapter(type)))
        } as FlowWithCTag<M>
        return flow.inner
            .onEach { listener.onMessage(it) }
            .onCompletion { onFlowComplete(type) }
            .launchIn(ImperiumScope.IO)
    }

    private fun onFlowComplete(type: KClass<out Message>) {
        val flow = flows[type]
        if (flow != null && flow.inner.subscriptionCount.value == 0) {
            flows.remove(type)
            try {
                channel.basicCancel(flow.tag)
            } catch (e: Exception) {
                logger.error("Failed to delete queue for ${type.simpleName ?: type.jvmName}", e)
            }
        }
    }

    private class FlowWithCTag<T : Message>(val tag: String) {
        val inner = MutableSharedFlow<T>()
    }

    private inner class RabbitmqAdapter<T : Message>(private val type: KClass<T>) : Consumer {
        override fun handleConsumeOk(consumerTag: String) = Unit
        override fun handleRecoverOk(consumerTag: String) = Unit
        override fun handleCancelOk(consumerTag: String) = Unit
        override fun handleCancel(consumerTag: String) =
            logger.error("Consumer for ${type.simpleName ?: type.jvmName} has been unexpectedly cancelled")
        override fun handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException) {
            if (!sig.isInitiatedByApplication) {
                logger.error("Consumer for ${type.simpleName ?: type.jvmName} has been shut down unexpectedly", sig)
            }
        }
        override fun handleDelivery(
            consumerTag: String,
            envelope: Envelope,
            properties: AMQP.BasicProperties,
            body: ByteArray,
        ) = runBlocking {
            // Have to call toString() because it's wrapped in another object
            val sender = properties.headers[SENDER_HEADER]?.toString()
            if (sender == null) {
                logger.warn("Received ${type.simpleName ?: type.jvmName} message without sender header from $envelope")
                return@runBlocking
            }
            if (sender == metadata.identifier.toString()) {
                return@runBlocking
            }

            if (body.isEmpty()) {
                logger.warn("Received empty ${type.simpleName ?: type.jvmName} message from $sender")
                return@runBlocking
            }
            if (body.size > MAX_OBJECT_SIZE) {
                logger.warn("Received ${type.simpleName ?: type.jvmName} message from $sender that is too large: ${body.size} bytes")
                return@runBlocking
            }

            val klassName = properties.headers[JAVA_CLASS_HEADER]?.toString()
            if (klassName == null) {
                logger.warn("Received ${type.simpleName ?: type.jvmName} message without Java class header from $sender")
                return@runBlocking
            }
            val klass = try {
                Class.forName(klassName, true, this@RabbitmqMessenger::class.java.classLoader).kotlin
            } catch (e: ClassNotFoundException) {
                logger.trace("Received ${type.simpleName ?: type.jvmName} message with unknown Java class from $sender: $klassName")
                return@runBlocking
            }
            if (!type.isSuperclassOf(klass)) {
                logger.warn("Received ${type.simpleName ?: type.jvmName} message with an unexpected Java superclass from $sender: $klassName")
                return@runBlocking
            }

            val parsed = try {
                gson.fromJson<T>(body.decodeToString(), klass.java)
            } catch (e: Exception) {
                logger.error("Failed to parse ${type.simpleName ?: type.jvmName} message from $sender", e)
                return@runBlocking
            }

            handleIncomingMessage(parsed)
        }
    }

    private suspend fun handleIncomingMessage(message: Message) {
        forEachMessageSuperclass(message::class) {
            val flow = flows[it] ?: return@forEachMessageSuperclass
            @Suppress("UNCHECKED_CAST")
            (flow.inner as MutableSharedFlow<Message>).emit(message)
        }
    }

    private suspend fun forEachMessageSuperclass(klass: KClass<out Message>, callback: suspend (KClass<out Message>) -> Unit) {
        callback(klass)
        klass.allSuperclasses.forEach {
            if (Message::class.isSuperclassOf(it)) {
                @Suppress("UNCHECKED_CAST")
                callback(it as KClass<out Message>)
            }
        }
    }

    companion object {
        private val logger by LoggerDelegate()
        const val IMPERIUM_EXCHANGE = "imperium"
        const val SENDER_HEADER = "Imperium-Sender"
        const val JAVA_CLASS_HEADER = "Imperium-Java-Class"
        const val MAX_OBJECT_SIZE = 2 * 1024 * 1024
    }
}
