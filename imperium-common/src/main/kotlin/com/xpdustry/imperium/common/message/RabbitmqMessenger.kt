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

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownSignalException
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.MessengerConfig
import com.xpdustry.imperium.common.misc.LoggerDelegate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

private typealias MessageOrRequest<T> = Pair<T, RabbitmqMessenger.RequestData?>

class RabbitmqMessenger(private val config: ImperiumConfig) : Messenger, ImperiumApplication.Listener {
    internal val flows = ConcurrentHashMap<KClass<out Message>, FlowWithCTag<out Message>>()
    private lateinit var channel: Channel
    private lateinit var connection: Connection

    override fun onImperiumInit() {
        val rabbit = config.messenger as MessengerConfig.RabbitMQ
        val factory =
            ConnectionFactory().apply {
                host = rabbit.host
                port = rabbit.port
                isAutomaticRecoveryEnabled = true

                if (username.isNotBlank()) {
                    username = rabbit.username
                    password = rabbit.password.value
                }

                if (rabbit.ssl) {
                    useSslProtocol(SSLContext.getDefault())
                }
            }

        connection = factory.newConnection(config.server.name)
        channel = connection.createChannel()
        channel.exchangeDeclare(IMPERIUM_EXCHANGE, BuiltinExchangeType.DIRECT, false, true, null)
    }

    override fun onImperiumExit() {
        channel.close()
        connection.close()
    }

    override suspend fun publish(message: Message, local: Boolean) = publish0(message, local, null)

    override suspend fun <R : Message> request(message: Message, timeout: Duration, responseKlass: KClass<R>): R? =
        withContext(ImperiumScope.IO.coroutineContext) {
            val request = RequestData(UUID.randomUUID(), false)
            val deferred = CompletableDeferred<R>()
            val job =
                handle(responseKlass) { (response, incoming) ->
                    if (incoming?.id == request.id && incoming.reply && deferred.isActive) {
                        deferred.complete(response)
                    }
                }
            if (!publish0(message, false, request)) {
                job.cancelAndJoin()
                return@withContext null
            }
            try {
                withTimeout(timeout) { deferred.await() }
            } catch (e: Exception) {
                job.cancelAndJoin()
                null
            }
        }

    @OptIn(InternalSerializationApi::class)
    private suspend fun <M : Message> publish0(message: M, local: Boolean, request: RequestData?) =
        withContext(ImperiumScope.IO.coroutineContext) {
            try {
                if (local) {
                    forEachMessageSuperclass(message::class) { klass -> handleIncomingMessage(klass, message, request) }
                }

                @Suppress("UNCHECKED_CAST")
                val json = Json.encodeToString((message::class as KClass<M>).serializer(), message)
                logger.trace("Publishing {} message: {}", message::class.simpleName ?: message::class.jvmName, json)
                val bytes = json.encodeToByteArray()
                val headers =
                    mutableMapOf(SENDER_HEADER to config.server.name, JAVA_CLASS_HEADER to message::class.jvmName)
                if (request != null) {
                    headers[REQUEST_ID_HEADER] = request.id.toString()
                    headers[REQUEST_REPLY_HEADER] = request.reply.toString()
                }
                val properties = AMQP.BasicProperties.Builder().headers(headers as Map<String, Any>).build()
                forEachMessageSuperclass(message::class) { klass ->
                    channel.basicPublish(IMPERIUM_EXCHANGE, klass.jvmName, properties, bytes)
                }

                true
            } catch (e: Exception) {
                logger.error("Failed to publish ${message::class.simpleName ?: message::class.jvmName} message", e)
                false
            }
        }

    override fun <M : Message> consumer(type: KClass<M>, listener: Messenger.ConsumerListener<M>): Job {
        return handle(type) { (message, _) -> listener.onMessage(message) }
    }

    override fun <M : Message, R : Message> function(type: KClass<M>, function: Messenger.FunctionListener<M, R>): Job {
        return handle(type) { (message, request) ->
            val response = function.onMessage(message) ?: return@handle
            publish0(response, false, request!!.copy(reply = true))
        }
    }

    private fun <M : Message> handle(type: KClass<M>, handler: Handler<M>): Job {
        @Suppress("UNCHECKED_CAST")
        val flow =
            flows.getOrPut(type) {
                val queue = channel.queueDeclare().queue
                channel.queueBind(queue, IMPERIUM_EXCHANGE, type.jvmName)
                FlowWithCTag(channel.basicConsume(queue, true, RabbitmqAdapter(type)))
            } as FlowWithCTag<M>
        return ImperiumScope.IO.launch { flow.inner.collect { handler.handle(it) } }
            .apply { invokeOnCompletion { onFlowComplete(type) } }
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

    internal class FlowWithCTag<T : Message>(val tag: String) {
        val inner = MutableSharedFlow<MessageOrRequest<T>>()
    }

    private fun interface Handler<T : Message> {
        suspend fun handle(messageOrRequest: MessageOrRequest<T>)
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

        @OptIn(InternalSerializationApi::class)
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
            if (sender == config.server.name) {
                return@runBlocking
            }

            if (body.isEmpty()) {
                logger.warn("Received empty ${type.simpleName ?: type.jvmName} message from $sender")
                return@runBlocking
            }
            if (body.size > MAX_OBJECT_SIZE) {
                logger.warn(
                    "Received ${type.simpleName ?: type.jvmName} message from $sender that is too large: ${body.size} bytes"
                )
                return@runBlocking
            }

            val klassName = properties.headers[JAVA_CLASS_HEADER]?.toString()
            if (klassName == null) {
                logger.warn(
                    "Received ${type.simpleName ?: type.jvmName} message without Java class header from $sender"
                )
                return@runBlocking
            }
            val klass =
                try {
                    Class.forName(klassName, true, this@RabbitmqMessenger::class.java.classLoader).kotlin
                } catch (e: ClassNotFoundException) {
                    logger.trace(
                        "Received ${type.simpleName ?: type.jvmName} message with unknown Java class from $sender: $klassName"
                    )
                    return@runBlocking
                }
            if (!type.isSuperclassOf(klass)) {
                logger.warn(
                    "Received ${type.simpleName ?: type.jvmName} message with an unexpected Java superclass from $sender: $klassName"
                )
                return@runBlocking
            }

            val json = body.decodeToString()
            val parsed =
                try {
                    @Suppress("UNCHECKED_CAST")
                    Json.decodeFromString(klass.serializer(), body.decodeToString()) as T
                } catch (e: Exception) {
                    logger.error("Failed to parse ${type.simpleName ?: type.jvmName} message from $sender", e)
                    return@runBlocking
                }

            val request =
                if (properties.headers.containsKey(REQUEST_ID_HEADER)) {
                    val uuid = UUID.fromString(properties.headers[REQUEST_ID_HEADER].toString())
                    val reply = properties.headers[REQUEST_REPLY_HEADER].toString().toBoolean()
                    RequestData(uuid, reply)
                } else null

            logger.trace("Received ${type.simpleName ?: type.jvmName} message from $sender: $json")
            handleIncomingMessage(type, parsed, request)
        }
    }

    private suspend fun <T : Message> handleIncomingMessage(klass: KClass<out T>, message: T, request: RequestData?) {
        val flow = flows[klass] ?: return
        @Suppress("UNCHECKED_CAST") (flow.inner as MutableSharedFlow<MessageOrRequest<T>>).emit(message to request)
    }

    private suspend fun forEachMessageSuperclass(
        klass: KClass<out Message>,
        callback: suspend (KClass<out Message>) -> Unit,
    ) {
        callback(klass)
        klass.allSuperclasses.forEach {
            if (Message::class.isSuperclassOf(it)) {
                @Suppress("UNCHECKED_CAST") callback(it as KClass<out Message>)
            }
        }
    }

    internal data class RequestData(val id: UUID, val reply: Boolean)

    companion object {
        private val logger by LoggerDelegate()
        const val IMPERIUM_EXCHANGE = "imperium"
        const val SENDER_HEADER = "Imperium-Sender"
        const val JAVA_CLASS_HEADER = "Imperium-Java-Class"
        const val REQUEST_ID_HEADER = "Imperium-Request-Id"
        const val REQUEST_REPLY_HEADER = "Imperium-Request-Reply"
        const val MAX_OBJECT_SIZE = 2 * 1024 * 1024
    }
}
