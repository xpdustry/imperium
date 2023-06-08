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

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.inject.Inject
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client.MongoCollection
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.database.mongo.MongoProvider
import com.xpdustry.foundation.common.database.mongo.MongoEntityManager.Companion.ID_FIELD
import java.util.UUID
import kotlin.reflect.KClass
import org.bson.Document
import org.bson.types.Binary
import org.objenesis.strategy.StdInstantiatorStrategy
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MongoMessenger @Inject constructor(private val mongo: MongoProvider) : Messenger, FoundationListener {

    private val uuid = UUID.randomUUID().toString()
    private lateinit var collection: MongoCollection<Document>

    @Suppress("UsePropertyAccessSyntax")
    private val kryo: Kryo = Kryo().apply {
        setRegistrationRequired(false)
        setAutoReset(true)
        setOptimizedGenerics(false)
        setInstantiatorStrategy(StdInstantiatorStrategy())
    }

    override fun onFoundationInit() {
        collection = mongo.database.getCollection("messenger")
    }

    override fun publish(message: Message): Mono<Void> = Mono.just(message)
        .map { Output(MAX_MESSAGE_SIZE).apply { kryo.writeClassAndObject(this, it) }.toBytes() }
        .map {
            Document()
                .append("class", message::class.qualifiedName!!)
                .append("message", it)
                .append("origin", uuid)
        }
        .flatMap { Mono.from(collection.insertOne(it)) }
        .then()

    override fun <M : Message> subscribe(message: KClass<M>): Flux<M> = Flux.from(
        collection.watch(getFilterFor(message))
    )
        .map { it.fullDocument!! }
        .flatMap { Mono.from(collection.deleteOne(Filters.eq(ID_FIELD, it["_id"]))).then(Mono.just(it)) }
        .map { it["message"]!! as Binary }
        .map { Input(it.data).run { kryo.readClassAndObject(this) } }
        .cast(message.java)

    private fun getFilterFor(message: KClass<out Message>) = listOf(
        Aggregates.match(
            Filters.and(
                Filters.eq("operationType", "insert"),
                Filters.eq("fullDocument.class", message.qualifiedName!!),
                Filters.ne("fullDocument.origin", uuid),
            )
        )
    )

    companion object {
        const val MAX_MESSAGE_SIZE = 16 * 1024
    }
}
