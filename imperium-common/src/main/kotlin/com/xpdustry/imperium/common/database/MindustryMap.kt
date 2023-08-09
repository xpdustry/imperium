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
package com.xpdustry.imperium.common.database
import org.bson.types.ObjectId
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

data class MindustryMap(
    override val id: ObjectId = ObjectId(),
    var name: String,
    var description: String,
    var author: String,
    var version: String,
    var width: Int,
    var height: Int,
    var playtime: Duration,
    var games: Int,
) : Entity<ObjectId>

data class Rating(
    override val id: ObjectId = ObjectId(),
    var map: ObjectId,
    var player: MindustryUUID,
    var score: Int,
    var difficulty: Difficulty,
) : Entity<ObjectId> {
    enum class Difficulty {
        EASY, NORMAL, HARD, EXPERT
    }
}

interface MindustryMapManager : EntityManager<ObjectId, MindustryMap> {
    fun findMapByName(name: String): Mono<MindustryMap>
    fun searchMaps(name: String): Flux<MindustryMap>
}

interface MindustryMapRatingManager : EntityManager<ObjectId, Rating> {
    fun findRatingByMapAndPlayer(map: ObjectId, player: MindustryUUID): Mono<Rating>
    fun computeScoreAverageByMap(map: ObjectId): Mono<Double>
    fun computeDifficultyAverageByMap(map: ObjectId): Mono<Rating.Difficulty>
}
