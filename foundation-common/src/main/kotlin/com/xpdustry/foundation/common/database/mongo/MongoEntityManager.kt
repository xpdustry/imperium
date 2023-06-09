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
package com.xpdustry.foundation.common.database.mongo

import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.reactivestreams.client.MongoCollection
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.database.Entity
import com.xpdustry.foundation.common.database.EntityManager
import kotlin.reflect.KClass
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.pojo.ClassModelBuilder
import org.bson.codecs.pojo.Convention
import org.bson.codecs.pojo.Conventions
import org.bson.codecs.pojo.PojoCodecProvider
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

abstract class MongoEntityManager<E : Entity<I>, I> protected constructor(private val mongo: MongoProvider) :
    EntityManager<I, E>, FoundationListener {

    protected lateinit var collection: MongoCollection<E>
        private set

    protected abstract val name: String
    protected abstract val type: KClass<E>
    protected open val codecs: List<Codec<*>> = emptyList()

    override fun onFoundationInit() {
        collection = mongo.database.getCollection(name, type.java).withCodecRegistry(
            CodecRegistries.fromProviders(
                MongoClientSettings.getDefaultCodecRegistry(),
                PojoCodecProvider.builder()
                    .register(type.java)
                    .conventions(
                        Conventions.DEFAULT_CONVENTIONS
                                + listOf(SnakeCaseConvention, Conventions.SET_PRIVATE_FIELDS_CONVENTION)
                    )
                    .build(),
                CodecRegistries.fromCodecs(codecs),
            )
        )
    }

    override fun save(entity: E): Mono<Void> =
        Mono.from(collection.replaceOne(Filters.eq(ID_FIELD, entity.id), entity, ReplaceOptions().upsert(true)))
            .then()

    override fun saveAll(entities: Iterable<E>): Mono<Void> =
        Flux.fromIterable(entities)
            .map { ReplaceOneModel(Filters.eq(ID_FIELD, it.id), it, ReplaceOptions().upsert(true)) }
            .collectList()
            .flatMap { Mono.from(collection.bulkWrite(it)) }
            .then()

    override fun findById(id: I): Mono<E> =
        Mono.from(collection.find(Filters.eq(ID_FIELD, id)).first())

    override fun findAll(): Flux<E> = Flux.from(collection.find())

    override fun exists(entity: E): Mono<Boolean> =
        Mono.from(collection.countDocuments(Filters.eq(ID_FIELD, entity.id))).map { it > 0 }

    override fun count(): Mono<Long> = Mono.from(collection.countDocuments())

    override fun deleteById(id: I): Mono<Void> =
        Mono.from(collection.deleteOne(Filters.eq(ID_FIELD, id))).then()

    override fun deleteAll(): Mono<Void> = Mono.from(collection.deleteMany(Filters.empty())).then()

    override fun deleteAll(entities: Iterable<E>): Mono<Void> =
        Flux.fromIterable(entities)
            .map(Entity<I>::id)
            .collectList()
            .flatMap { Mono.from(collection.deleteMany(Filters.`in`(ID_FIELD, it))) }
            .then()

    companion object {
        const val ID_FIELD = "_id"
    }

    private object SnakeCaseConvention : Convention {
        override fun apply(classModelBuilder: ClassModelBuilder<*>) {
            classModelBuilder.propertyModelBuilders.forEach {
                it.readName(it.readName.camelToSnakeCase())
                it.writeName(it.writeName.camelToSnakeCase())
            }
        }

        // https://www.baeldung.com/kotlin/convert-camel-case-snake-case
        private fun String.camelToSnakeCase(): String {
            return this.fold(StringBuilder()) { acc, c ->
                acc.append(if (acc.isNotEmpty() && c.isUpperCase()) "_${c.lowercase()}" else c.lowercase())
            }.toString()
        }
    }
}
