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
package com.xpdustry.imperium.common.database.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.AggregateFlow
import com.mongodb.kotlin.client.coroutine.FindFlow
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.xpdustry.imperium.common.database.Entity
import kotlinx.coroutines.flow.firstOrNull
import org.bson.conversions.Bson
import kotlin.reflect.KClass

internal class MongoEntityCollection<E : Entity<I>, I : Any>(private val collection: MongoCollection<E>) {
    suspend fun save(entity: E) {
        collection.replaceOne(Filters.eq(ID_FIELD, entity._id), entity, ReplaceOptions().upsert(true))
    }

    suspend fun saveAll(entities: Iterable<E>) {
        collection.bulkWrite(entities.map { ReplaceOneModel(Filters.eq(ID_FIELD, it._id), it, ReplaceOptions().upsert(true)) })
    }

    fun find(filters: Bson): FindFlow<E> =
        collection.find(filters)

    fun findAll(): FindFlow<E> =
        collection.find()

    suspend fun findById(id: I): E? =
        collection.find(Filters.eq(ID_FIELD, id)).firstOrNull()

    suspend fun exists(entity: E): Boolean =
        collection.countDocuments(Filters.eq(ID_FIELD, entity._id)) > 0

    suspend fun count(filters: Bson): Long =
        collection.countDocuments(filters)

    suspend fun countAll(): Long =
        collection.countDocuments()

    suspend fun deleteById(id: I) {
        collection.deleteOne(Filters.eq(ID_FIELD, id))
    }

    suspend fun deleteAll() {
        collection.deleteMany(Filters.empty())
    }

    suspend fun deleteAll(entities: Iterable<E>) {
        collection.deleteMany(Filters.`in`(ID_FIELD, entities.map(Entity<I>::_id)))
    }

    suspend fun index(indexes: Bson, options: IndexOptions.() -> Unit) {
        collection.createIndex(indexes, IndexOptions().apply(options))
    }

    fun <R : Any> aggregate(vararg pipeline: Bson, result: KClass<R>): AggregateFlow<R> =
        collection.aggregate(pipeline.toList(), result.java)

    companion object {
        const val ID_FIELD = "_id"
    }
}
