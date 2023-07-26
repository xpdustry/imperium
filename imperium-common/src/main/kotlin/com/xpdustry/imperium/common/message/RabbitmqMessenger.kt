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
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownSignalException
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.application.ImperiumMetadata
import com.xpdustry.imperium.common.config.ImperiumConfig
import org.objenesis.strategy.StdInstantiatorStrategy
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class RabbitmqMessenger(private val config: ImperiumConfig, private val metadata: ImperiumMetadata) : Messenger, ImperiumApplication.Listener {

    // TODO Test if kryo is still broken
    @Suppress("UsePropertyAccessSyntax")
    private val kryo = Kryo().apply {
        setRegistrationRequired(false)
        setAutoReset(true)
        setOptimizedGenerics(false)
        setInstantiatorStrategy(StdInstantiatorStrategy())
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
        channel.exchangeDeclare("imperium-${metadata.version}", "direct", true)
    }

    override fun onImperiumExit() {
        channel.close()
        connection.close()
    }

    override fun publish(message: Message): Mono<Void> = Mono.just(message)
        .map { Output(MAX_OBJECT_SIZE).also { kryo.writeObject(it, message) }.toBytes() }
        .map {
            channel.basicPublish(
                "imperium-${metadata.version}",
                message::class.qualifiedName,
                AMQP.BasicProperties.Builder().headers(mapOf(ORIGIN_HEADER to metadata.identifier)).build(),
                it,
            )
        }
        .then()

    override fun <M : Message> on(type: KClass<M>): Flux<M> {
        return Mono.fromSupplier {
            val queue = channel.queueDeclare().queue
            channel.queueBind(queue, "imperium-${metadata.version}", type.jvmName)
            queue
        }.flatMapMany { queue ->
            Flux.create {
                val adapter = RabbitmqFluxAdapter(it, type)
                val consumerTag = channel.basicConsume(queue, true, adapter)
                it.onDispose {
                    if (channel.isOpen) {
                        channel.basicCancel(consumerTag)
                        channel.queueDelete(queue, false, false)
                    }
                }
            }
        }
    }

    private inner class RabbitmqFluxAdapter<T : Message>(private val sink: FluxSink<T>, private val type: KClass<T>) : Consumer {

        override fun handleConsumeOk(consumerTag: String) = Unit

        override fun handleRecoverOk(consumerTag: String) = Unit

        override fun handleCancelOk(consumerTag: String) {
            sink.complete()
        }

        override fun handleCancel(consumerTag: String) {
            sink.complete()
        }

        override fun handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException) {
            if (sig.isInitiatedByApplication) return
            sink.error(sig)
        }

        override fun handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: ByteArray) {
            // Have to call toString() because it's wrapped in another object
            if (properties.headers[ORIGIN_HEADER]?.toString() == metadata.identifier) return
            sink.next(Input(body).use { input -> kryo.readObject(input, type.java) })
        }
    }

    companion object {
        const val ORIGIN_HEADER = "Imperium-Origin"
        const val MAX_OBJECT_SIZE = 1024 * 1024
    }
}
