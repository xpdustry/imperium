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
import com.xpdustry.foundation.common.database.Entity
import com.xpdustry.foundation.common.database.EntityManager
import com.xpdustry.foundation.common.misc.toValueFlux
import com.xpdustry.foundation.common.misc.toValueMono
import org.litote.kmongo.eq
import org.litote.kmongo.`in`
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

const val ID_FIELD = "_id"

abstract class MongoEntityManager<E : Entity<I>, I> protected constructor(
    protected val collection: MongoCollection<E>,
) : EntityManager<I, E> {

    override fun save(entity: E): Mono<Void> =
        collection.replaceOne(Entity<I>::id eq entity.id, entity, ReplaceOptions().upsert(true)).toValueMono().then()

    override fun saveAll(entities: Iterable<E>): Mono<Void> =
        entities.toValueFlux()
            .map { ReplaceOneModel(Entity<I>::id eq it.id, it, ReplaceOptions().upsert(true)) }
            .collectList()
            .flatMap { collection.bulkWrite(it).toValueMono().then() }

    override fun findById(id: I): Mono<E> =
        collection.find(
            Entity<I>::id eq id,
        ).first().toValueMono()

    override fun findAll(): Flux<E> = collection.find().toValueFlux()

    override fun exists(entity: E): Mono<Boolean> =
        collection.countDocuments(Entity<I>::id eq entity.id).toValueMono().map { it > 0 }

    override fun count(): Mono<Long> = collection.countDocuments().toValueMono()

    override fun deleteById(id: I): Mono<Void> =
        collection.deleteOne(Entity<I>::id eq id).toValueMono().then()

    override fun deleteAll(): Mono<Void> = collection.deleteMany(Filters.empty()).toValueMono().then()

    override fun deleteAll(entities: Iterable<E>): Mono<Void> =
        entities.toValueFlux()
            .map(Entity<I>::id)
            .collectList()
            .flatMap { ids -> collection.deleteMany(Entity<I>::id `in` ids).toValueMono().then() }
}
