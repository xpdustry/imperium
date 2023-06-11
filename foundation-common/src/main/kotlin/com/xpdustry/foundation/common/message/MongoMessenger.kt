/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.common.message

import com.google.inject.Inject
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client.MongoCollection
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.database.mongo.ID_FIELD
import com.xpdustry.foundation.common.database.mongo.MongoProvider
import com.xpdustry.foundation.common.misc.toErrorMono
import com.xpdustry.foundation.common.misc.toValueMono
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.ByteBufNIO
import org.bson.Document
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.io.BasicOutputBuffer
import org.bson.io.ByteBufferBsonInput
import org.bson.types.Binary
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import com.xpdustry.foundation.common.misc.toValueFlux
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class MongoMessenger @Inject constructor(private val mongo: MongoProvider) : Messenger, FoundationListener {

    private val uuid = UUID.randomUUID().toString()
    private lateinit var collection: MongoCollection<Document>

    override fun onFoundationInit() {
        collection = mongo.database.getCollection("messenger")
    }

    override fun publish(message: Message): Mono<Void> = message.toValueMono()
        .flatMap(this::write)
        .map {
            Document()
                .append("class", message::class.jvmName)
                .append("message", it)
                .append("origin", uuid)
        }
        .flatMap { collection.insertOne(it).toValueMono().then() }

    private fun write(message: Message): Mono<ByteArray> {
        val output = BasicOutputBuffer(MAX_MESSAGE_SIZE)
        BsonBinaryWriter(output).use {
            collection.codecRegistry.get(message.javaClass).encode(it, message, ENCODER_CTX)
            if (output.position > MAX_MESSAGE_SIZE) {
                return@write IllegalArgumentException("Message is too large").toErrorMono()
            }
            return@write output.toByteArray().toValueMono()
        }
    }

    override fun <M : Message> subscribe(type: KClass<M>): Flux<M> = collection.watch(
        listOf(
            Aggregates.match(
                Filters.and(
                    Filters.eq("operationType", "insert"),
                    Filters.eq("fullDocument.class", type.jvmName),
                    Filters.ne("fullDocument.origin", uuid),
                ),
            ),
        ),
    ).toValueFlux()
        .map { it.fullDocument!! }
        .flatMap { collection.deleteOne(Filters.eq(ID_FIELD, it["_id"])).toValueMono().thenReturn(it) }
        .flatMap { read(it["message"]!! as Binary, type) }
        .cast(type.java)

    private fun <M : Message> read(message: Binary, type: KClass<M>): Mono<M> {
        val buffer = ByteBufNIO(ByteBuffer.wrap(message.data))
        if (buffer.remaining() > MAX_MESSAGE_SIZE) {
            return Mono.empty()
        }
        val input = ByteBufferBsonInput(buffer)
        return BsonBinaryReader(input).use {
            collection.codecRegistry.get(type.java).decode(it, DECODER_CTX)
        }.toValueMono()
    }

    companion object {
        private const val MAX_MESSAGE_SIZE = 8 * 1024
        private val ENCODER_CTX = EncoderContext.builder().build()
        private val DECODER_CTX = DecoderContext.builder().build()
    }
}
