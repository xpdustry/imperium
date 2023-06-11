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

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.reactivestreams.client.MongoCollection
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.database.Entity
import com.xpdustry.foundation.common.database.EntityManager
import com.xpdustry.foundation.common.misc.toValueFlux
import com.xpdustry.foundation.common.misc.toValueMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

const val ID_FIELD = "_id"

abstract class MongoEntityManager<E : Entity<I>, I> protected constructor(
    private val mongo: MongoProvider,
    private val name: String,
    private val type: KClass<E>,
) : EntityManager<I, E>, FoundationListener {

    protected lateinit var collection: MongoCollection<E>
        private set

    override fun onFoundationInit() {
        collection = mongo.database.getCollection(name, type.java)
    }

    override fun save(entity: E): Mono<Void> =
        collection.replaceOne(Filters.eq(ID_FIELD, entity.id), entity, ReplaceOptions().upsert(true)).toValueMono().then()

    override fun saveAll(entities: Iterable<E>): Mono<Void> =
        entities.toValueFlux()
            .map { ReplaceOneModel(Filters.eq(ID_FIELD, it.id), it, ReplaceOptions().upsert(true)) }
            .collectList()
            .flatMap { collection.bulkWrite(it).toValueMono().then() }

    override fun findById(id: I): Mono<E> =
        collection.find(Filters.eq(ID_FIELD, id)).first().toValueMono()

    override fun findAll(): Flux<E> = collection.find().toValueFlux()

    override fun exists(entity: E): Mono<Boolean> =
        collection.countDocuments(Filters.eq(ID_FIELD, entity.id)).toValueMono().map { it > 0 }

    override fun count(): Mono<Long> = collection.countDocuments().toValueMono()

    override fun deleteById(id: I): Mono<Void> =
        collection.deleteOne(Filters.eq(ID_FIELD, id)).toValueMono().then()

    override fun deleteAll(): Mono<Void> = collection.deleteMany(Filters.empty()).toValueMono().then()

    override fun deleteAll(entities: Iterable<E>): Mono<Void> =
        entities.toValueFlux()
            .map(Entity<I>::id)
            .collectList()
            .flatMap { collection.deleteMany(Filters.`in`(ID_FIELD, it)).toValueMono().then() }
}
