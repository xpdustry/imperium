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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.SQLDatabase
import com.xpdustry.imperium.common.misc.LoggerDelegate
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializerOrNull

internal class SQLMessageService(
    private val database: SQLDatabase,
    private val config: ImperiumConfig,
    private val context: CoroutineScope,
) : MessageService, ImperiumApplication.Listener {
    internal val subscribers = MutableSharedFlow<Message>()
    private var cursor: Long = 0
    private lateinit var pollJob: Job

    override fun onImperiumInit() {
        runBlocking {
            database.transaction {
                """
                CREATE TABLE IF NOT EXISTS `message_queue_v2` (
                    `counter`       BIGINT          NOT NULL AUTO_INCREMENT,
                    `created_at`    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    `sender`        VARCHAR(64)     NOT NULL,
                    `topic`         VARCHAR(256)    NOT NULL,
                    `payload`       TEXT            NOT NULL,
                    CONSTRAINT `pk_message_queue_v2`
                        PRIMARY KEY (`counter`)
                );
                """
                    .asPreparedStatement()
                    .executeSingleUpdate()

                """
                CREATE INDEX IF NOT EXISTS `ix_message_queue_v2__created_at`
                    ON `message_queue_v2` (`created_at`);
                """
                    .asPreparedStatement()
                    .executeSingleUpdate()

                """
                CREATE EVENT IF NOT EXISTS `message_queue_v2_cleanup`
                ON SCHEDULE EVERY 10 MINUTE
                DO
                    DELETE FROM `message_queue_v2`
                    WHERE `created_at` < CURRENT_TIMESTAMP() - INTERVAL 1 HOUR;
                """
                    .asPreparedStatement()
                    .executeSingleUpdate()

                cursor =
                    "SELECT `counter` FROM `message_queue_v2` ORDER BY `counter` DESC LIMIT 1;"
                        .asPreparedStatement()
                        .executeSelect { getLong("counter")!! }
                        .firstOrNull() ?: cursor
            }

            poll()

            pollJob =
                context.launch {
                    while (isActive) {
                        delay(600.milliseconds)
                        try {
                            poll()
                        } catch (e: Exception) {
                            LOGGER.error("An error occurred while polling messages", e)
                        }
                    }
                }
        }
    }

    override fun onImperiumExit() {
        runBlocking { pollJob.cancelAndJoin() }
    }

    @OptIn(InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    override suspend fun <M : Message> broadcast(message: M, local: Boolean) {
        val topic = message::class.jvmName
        val serializer = message::class.serializerOrNull() as? SerializationStrategy<M>
        if (serializer == null) {
            LOGGER.error("Cannot serialize $message ${message::class.jvmName}")
            return
        }
        val payload = SAFE_JSON.encodeToString(serializer, message)
        database.transaction {
            "INSERT INTO `message_queue_v2` (`sender`, `topic`, `payload`) VALUES (?, ?, ?);"
                .asPreparedStatement()
                .push(config.server.name)
                .push(topic)
                .push(payload)
                .executeSingleUpdate()
        }
        if (local) {
            context.launch { subscribers.emit(message) }
        }
        LOGGER.trace("Broadcasted message (topic={}, payload={})", topic, payload)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <M : Message> subscribe(
        type: KClass<M>,
        subscriber: MessageService.Subscriber<M>,
    ): MessageService.Subscriber.Handle {
        val job =
            context.launch {
                subscribers.filterIsInstance(type).collect {
                    try {
                        subscriber.onMessage(it)
                    } catch (e: Exception) {
                        LOGGER.error("An error occurred while processing message of type {}", type.simpleName, e)
                    }
                }
            }
        return MessageService.Subscriber.Handle { job.cancel() }
    }

    private suspend fun poll() {
        val queue =
            try {
                database.transaction {
                    """
                    SELECT `counter`, `sender`, `topic`, `payload`
                    FROM `message_queue_v2`
                    WHERE `counter` > ? AND `sender` != ? AND (`created_at` > CURRENT_TIMESTAMP() - INTERVAL 30 SECOND)
                    ORDER BY `counter` ASC;
                    """
                        .asPreparedStatement()
                        .push(cursor)
                        .push(config.server.name)
                        .executeSelect {
                            RawMessage(
                                getLong("counter")!!,
                                getString("sender")!!,
                                getString("topic")!!,
                                getString("payload")!!,
                            )
                        }
                }
            } catch (e: Exception) {
                LOGGER.error("An error occurred while polling messages", e)
                return
            }

        queue.forEach { message ->
            context.launch {
                val result = deserialize(message.topic, message.payload) ?: return@launch
                subscribers.emit(result)
            }
        }

        cursor = queue.lastOrNull()?.counter ?: cursor
        LOGGER.trace("Polled {} messages, advancing cursor to {}: {}", queue.size, cursor, queue)
    }

    @OptIn(InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun deserialize(topic: String, payload: String): Message? {
        var type: KClass<*>
        try {
            type = Class.forName(topic, false, javaClass.classLoader).kotlin
        } catch (_: ClassNotFoundException) {
            LOGGER.trace("Cannot find topic {}", topic)
            return null
        }
        if (!Message::class.isSuperclassOf(type)) {
            LOGGER.error("Expected message implementing Message, got $type")
            return null
        }
        val serializer = type.serializerOrNull() as? DeserializationStrategy<Message>
        if (serializer == null) {
            LOGGER.error("{} is not @Serializable", type.jvmName)
            return null
        }
        try {
            return SAFE_JSON.decodeFromString(serializer, payload)
        } catch (e: Exception) {
            LOGGER.error("Failed to deserialize message", e)
            return null
        }
    }

    data class RawMessage(val counter: Long, val sender: String, val topic: String, val payload: String)

    companion object {
        private val LOGGER by LoggerDelegate()
        private val SAFE_JSON = Json {
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }
}
