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
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.misc.LoggerDelegate
import org.objenesis.strategy.StdInstantiatorStrategy
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import java.net.Inet4Address
import java.net.Inet6Address
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

    private lateinit var connection: Connection
    private lateinit var channel: Channel

    override fun onImperiumInit() {
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

    override fun publish(message: Message): Mono<Void> = Mono.just(message)
        .map { Output(MAX_OBJECT_SIZE).also { kryo.writeObject(it, message) }.toBytes() }
        .map {
            channel.basicPublish(
                IMPERIUM_EXCHANGE,
                message::class.jvmName,
                AMQP.BasicProperties.Builder().headers(mapOf(SENDER_HEADER to metadata.identifier)).build(),
                it,
            )
        }
        .then()

    override fun <M : Message> on(type: KClass<M>): Flux<M> {
        return Mono.fromSupplier {
            val queue = channel.queueDeclare().queue
            channel.queueBind(queue, IMPERIUM_EXCHANGE, type.jvmName)
            queue
        }.flatMapMany { queue ->
            Flux.create { sink ->
                val consumerTag = channel.basicConsume(queue, true, RabbitmqFluxAdapter(sink, type))
                sink.onDispose { if (channel.isOpen) channel.basicCancel(consumerTag) }
            }
        }
    }

    private inner class RabbitmqFluxAdapter<T : Message>(private val sink: FluxSink<T>, private val type: KClass<T>) : Consumer {
        override fun handleConsumeOk(consumerTag: String) = Unit
        override fun handleRecoverOk(consumerTag: String) = Unit
        override fun handleCancelOk(consumerTag: String) = sink.complete()
        override fun handleCancel(consumerTag: String) = sink.complete()
        override fun handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException) {
            if (!sig.isInitiatedByApplication) sink.error(sig)
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
                try {
                    sink.next(Input(body).use { input -> kryo.readObject(input, type.java) })
                } catch (e: Exception) {
                    logger.error("Failed to handle message from $sender of type ${type.jvmName}", e)
                    return
                }
            }
        }
    }

    companion object {
        val logger by LoggerDelegate()
        const val IMPERIUM_EXCHANGE = "imperium"
        const val SENDER_HEADER = "Imperium-Sender"
        const val MAX_OBJECT_SIZE = 1024 * 1024
    }
}
