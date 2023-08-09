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

import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Projections
import com.mongodb.reactivestreams.client.MongoCollection
import com.xpdustry.imperium.common.database.MindustryMap
import com.xpdustry.imperium.common.database.MindustryMapManager
import com.xpdustry.imperium.common.database.MindustryMapRatingManager
import com.xpdustry.imperium.common.database.MindustryUUID
import com.xpdustry.imperium.common.database.Rating
import com.xpdustry.imperium.common.misc.toValueFlux
import com.xpdustry.imperium.common.misc.toValueMono
import org.bson.BsonDocument
import org.bson.types.ObjectId
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.math.roundToInt

class MongoMindustryMapManager(collection: MongoCollection<MindustryMap>) : MongoEntityManager<MindustryMap, ObjectId>(collection), MindustryMapManager {
    init {
        collection.createIndex(Indexes.text("name"), IndexOptions().unique(true)).toValueMono().block()
    }

    override fun findMapByName(name: String): Mono<MindustryMap> = collection.find(Filters.eq("name", name)).first().toValueMono()
    override fun searchMaps(name: String): Flux<MindustryMap> = collection.find(Filters.text(name))
        .projection(Projections.metaTextScore("score"))
        .sort(Projections.metaTextScore("score"))
        .toValueFlux()
}

class MongoMindustryMapRatingManager(collection: MongoCollection<Rating>) : MongoEntityManager<Rating, ObjectId>(collection), MindustryMapRatingManager {
    init {
        collection.createIndex(Indexes.ascending("map", "player"), IndexOptions().unique(true)).toValueMono().block()
    }

    override fun findRatingByMapAndPlayer(map: ObjectId, player: MindustryUUID): Mono<Rating> =
        collection.find(Filters.and(Filters.eq("map", map), Filters.eq("player", player))).first().toValueMono()

    override fun computeScoreAverageByMap(map: ObjectId): Mono<Double> =
        collection.aggregate(
            listOf(
                Aggregates.match(Filters.eq("map", map)),
                Aggregates.group("\$map", Accumulators.avg("average", "\$score")),
            ),
            BsonDocument::class.java,
        )
            .toValueMono()
            .map { it.getDouble("average").value }

    override fun computeDifficultyAverageByMap(map: ObjectId): Mono<Rating.Difficulty> =
        collection.aggregate(
            listOf(
                Aggregates.match(Filters.eq("map", map)),
                Aggregates.group("\$difficulty", Accumulators.sum("count", 1)),
            ),
            BsonDocument::class.java,
        )
            .toValueFlux()
            .collectMap({ Rating.Difficulty.valueOf(it.getString("_id").value) }, { it.getInt32("count").value })
            .map { difficulties ->
                if (difficulties.isEmpty()) {
                    return@map Rating.Difficulty.NORMAL
                }
                val average = difficulties.entries.sumOf { it.value * it.key.ordinal }.toDouble() / difficulties.values.sum().toDouble()
                return@map Rating.Difficulty.entries[average.roundToInt()]
            }
}
