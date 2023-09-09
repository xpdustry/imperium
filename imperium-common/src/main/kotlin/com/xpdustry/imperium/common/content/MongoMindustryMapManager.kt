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
package com.xpdustry.imperium.common.content

import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.xpdustry.imperium.common.account.MindustryUUID
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.mongo.MongoEntityCollection
import com.xpdustry.imperium.common.mongo.MongoProvider
import com.xpdustry.imperium.common.storage.S3Object
import com.xpdustry.imperium.common.storage.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.BsonDocument
import org.bson.types.ObjectId
import java.io.InputStream
import kotlin.math.roundToInt

internal class MongoMindustryMapManager(
    private val mongo: MongoProvider,
    private val storage: Storage,
) : MindustryMapManager, ImperiumApplication.Listener {

    private lateinit var maps: MongoEntityCollection<MindustryMap, ObjectId>
    private lateinit var ratings: MongoEntityCollection<Rating, ObjectId>

    override fun onImperiumInit() {
        maps = mongo.getCollection("maps", MindustryMap::class)
        ratings = mongo.getCollection("map_ratings", Rating::class)
        runBlocking {
            maps.index(Indexes.compoundIndex(Indexes.descending("name"))) {
                name("name_index").version(1).unique(true)
            }
        }
    }

    override suspend fun findMapById(id: ObjectId): MindustryMap? =
        maps.findById(id)

    override suspend fun findMapByName(name: String): MindustryMap? =
        maps.find(Filters.eq("name", name)).firstOrNull()

    override suspend fun findMaps(server: String?): Flow<MindustryMap> {
        return maps.find(if (server == null) Filters.empty() else Filters.`in`(server, "servers"))
            .sort(Sorts.descending("name"))
    }

    override suspend fun findRatingByMapAndPlayer(map: ObjectId, player: MindustryUUID): Rating? =
        ratings.find(Filters.and(Filters.eq("map", map), Filters.eq("player", player))).firstOrNull()

    override suspend fun computeAverageScoreByMap(map: ObjectId): Double {
        if (ratings.count(Filters.eq("map", map)) == 0L) {
            return 3.0
        }
        return ratings.aggregate(
            Aggregates.match(Filters.eq("map", map)),
            Aggregates.group("\$map", Accumulators.avg("average", "\$score")),
            result = BsonDocument::class,
        )
            .map { it.getDouble("average").value }
            .first()
    }

    override suspend fun computeAverageDifficultyByMap(map: ObjectId): Rating.Difficulty {
        if (ratings.count(Filters.eq("map", map)) == 0L) {
            return Rating.Difficulty.NORMAL
        }
        val difficulties = ratings.aggregate(
            Aggregates.match(Filters.eq("map", map)),
            Aggregates.group("\$difficulty", Accumulators.sum("count", 1)),
            result = BsonDocument::class,
        )
            .toList()
            .associateBy({ Rating.Difficulty.valueOf(it.getString("_id").value) }, { it.getInt32("count").value })
        if (difficulties.isEmpty()) {
            return Rating.Difficulty.NORMAL
        }
        val average = difficulties.entries.sumOf { it.value * it.key.ordinal }.toDouble() / difficulties.values.sum().toDouble()
        return Rating.Difficulty.entries[average.roundToInt()]
    }

    override suspend fun saveMap(map: MindustryMap, stream: InputStream) {
        maps.save(map)
        storage.getBucket("imperium-maps", create = true)!!.putObject("pool/${map._id}.msav", stream)
    }

    override suspend fun getMapObject(map: ObjectId): S3Object? =
        storage.getBucket("imperium-maps", create = true)!!.getObject("pool/$map.msav")

    override suspend fun updateMapById(id: ObjectId, updater: suspend MindustryMap.() -> Unit) {
        maps.findById(id)?.let {
            updater(it)
            maps.save(it)
        }
    }
}
