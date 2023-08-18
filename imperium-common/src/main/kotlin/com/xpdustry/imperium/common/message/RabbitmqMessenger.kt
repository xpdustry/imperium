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

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.objenesis.strategy.StdInstantiatorStrategy
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

// TODO The current system does not intercept the subclasses of a message type. We need to fix this.
class RabbitmqMessenger(private val config: ImperiumConfig, private val metadata: ImperiumMetadata) : Messenger, ImperiumApplication.Listener {

    private val kryo = Kryo().apply {
        instantiatorStrategy = StdInstantiatorStrategy()
        setRegistrationRequired(false)
        setAutoReset(true)
        setOptimizedGenerics(false)
        addDefaultSerializer(Inet4Address::class.java, InetAddressSerializer)
        addDefaultSerializer(Inet6Address::class.java, InetAddressSerializer)
    }

    private val flows = ConcurrentHashMap<KClass<out Message>, FlowWithCTag<out Message>>()

    private lateinit var connection: Connection
    private lateinit var channel: Channel

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
                useSslProtocol()
            }
        }

        connection = factory.newConnection(metadata.identifier)
        channel = connection.createChannel()
        channel.exchangeDeclare(IMPERIUM_EXCHANGE, BuiltinExchangeType.DIRECT, false, true, null)
    }

    override fun onImperiumExit() {
        channel.close()
        connection.close()
    }

    override suspend fun publish(message: Message) = withContext(ImperiumScope.IO.coroutineContext) {
        channel.basicPublish(
            IMPERIUM_EXCHANGE,
            message::class.jvmName,
            AMQP.BasicProperties.Builder().headers(mapOf(SENDER_HEADER to metadata.identifier)).build(),
            Output(MAX_OBJECT_SIZE).also { kryo.writeObject(it, message) }.toBytes(),
        )
    }

    override fun <M : Message> subscribe(type: KClass<M>, listener: Messenger.Listener<M>): Job {
        @Suppress("UNCHECKED_CAST")
        val flow = flows.getOrPut(type) {
            val queue = channel.queueDeclare().queue
            channel.queueBind(queue, IMPERIUM_EXCHANGE, type.jvmName)
            FlowWithCTag(
                MutableSharedFlow<M>(),
                channel.basicConsume(queue, true, RabbitmqFlowAdapter(type)),
            )
        } as FlowWithCTag<out M>
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
                channel.basicCancel(flow.cTag)
            } catch (e: Exception) {
                logger.warn("Failed to delete queue for type ${type.jvmName}", e)
            }
        }
    }

    private data class FlowWithCTag<T : Message>(val inner: MutableSharedFlow<T>, val cTag: String)

    private inner class RabbitmqFlowAdapter<T : Message>(private val type: KClass<T>) : Consumer {
        override fun handleConsumeOk(consumerTag: String) = Unit
        override fun handleRecoverOk(consumerTag: String) = Unit
        override fun handleCancelOk(consumerTag: String) = Unit
        override fun handleCancel(consumerTag: String) = logger.error("Consumer for type ${type.jvmName} was been unexpectedly cancelled")
        override fun handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException) {
            if (!sig.isInitiatedByApplication) logger.error("Consumer for type ${type.jvmName} was shut down unexpectedly", sig)
        }
        override fun handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: ByteArray) {
            // Have to call toString() because it's wrapped in another object
            val sender = properties.headers[SENDER_HEADER]?.toString()
            if (sender == null) {
                logger.warn("Received message without sender header from $envelope of type ${type.jvmName}")
            } else if (sender == metadata.identifier) {
                return
            } else if (body.isEmpty()) {
                logger.warn("Received empty message from $sender of type ${type.jvmName}")
            } else if (body.size > MAX_OBJECT_SIZE) {
                logger.warn("Received message from $sender that is too large of type ${type.jvmName}: ${body.size} bytes")
            } else {
                ImperiumScope.IO.launch {
                    try {
                        val message = Input(body).use { input -> kryo.readObject(input, type.java) }
                        @Suppress("UNCHECKED_CAST")
                        (flows[type]?.inner as MutableSharedFlow<T>?)?.emit(message)
                    } catch (e: Exception) {
                        logger.error("Failed to handle message from $sender of type ${type.jvmName}", e)
                    }
                }
            }
        }
    }

    companion object {
        private val logger by LoggerDelegate()
        const val IMPERIUM_EXCHANGE = "imperium"
        const val SENDER_HEADER = "Imperium-Sender"
        const val MAX_OBJECT_SIZE = 1024 * 1024
    }
}
