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
package com.xpdustry.imperium.common.account.sql

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.SnowflakeGenerator
import com.xpdustry.imperium.common.storage.StorageBucket
import java.io.InputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

interface SQLMapManager {

    suspend fun findMapBySnowflake(snowflake: Snowflake): MindustryMap?

    suspend fun findMapByName(name: String): MindustryMap?

    suspend fun findAllMapsByGamemode(gamemode: MindustryGamemode): Flow<MindustryMap>

    suspend fun findRatingByMapAndUser(map: Snowflake, user: Snowflake): MindustryMap.Rating?

    suspend fun findAllMaps(): Flow<MindustryMap>

    suspend fun computeAverageScoreByMap(snowflake: Snowflake): Double

    suspend fun computeAverageDifficultyByMap(snowflake: Snowflake): MindustryMap.Difficulty

    suspend fun deleteMapById(snowflake: Snowflake): Boolean

    suspend fun createMap(
        name: String,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: InputStream
    ): Snowflake

    suspend fun updateMap(
        snowflake: Snowflake,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: InputStream
    )

    suspend fun getMapObject(map: Snowflake): StorageBucket.S3Object

    suspend fun searchMapByName(query: String): Flow<MindustryMap>

    suspend fun getMapGamemodes(map: Snowflake): Set<MindustryGamemode>

    suspend fun setMapGamemodes(map: Snowflake, gamemodes: Set<MindustryGamemode>)
}

class SimpleSQLMapManager(
    private val database: Database,
    private val storage: StorageBucket,
    private val generator: SnowflakeGenerator
) : SQLMapManager, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        transaction(database) {
            SchemaUtils.create(
                MindustryMapTable, MindustryMapGamemodeTable, MindustryMapRatingTable)
        }
    }

    override suspend fun findMapBySnowflake(snowflake: Snowflake): MindustryMap? =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapTable.select { MindustryMapTable.id eq snowflake }
                .firstOrNull()
                ?.toMindustryMap()
        }

    override suspend fun findMapByName(name: String): MindustryMap? =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapTable.select { MindustryMapTable.name eq name }
                .firstOrNull()
                ?.toMindustryMap()
        }

    override suspend fun findAllMapsByGamemode(gamemode: MindustryGamemode): Flow<MindustryMap> =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            (MindustryMapTable leftJoin MindustryMapGamemodeTable)
                .select { MindustryMapGamemodeTable.gamemode eq gamemode }
                .asFlow()
                .map { it.toMindustryMap() }
        }

    override suspend fun findRatingByMapAndUser(
        map: Snowflake,
        user: Snowflake
    ): MindustryMap.Rating? =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapRatingTable.select {
                    (MindustryMapRatingTable.map eq map) and (MindustryMapRatingTable.user eq user)
                }
                .firstOrNull()
                ?.toMindustryMapRating()
        }

    override suspend fun findAllMaps(): Flow<MindustryMap> =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapTable.selectAll().asFlow().map { it.toMindustryMap() }
        }

    override suspend fun computeAverageScoreByMap(snowflake: Snowflake): Double =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapRatingTable.slice(MindustryMapRatingTable.score.avg())
                .select { MindustryMapRatingTable.map eq snowflake }
                .firstOrNull()
                ?.get(MindustryMapRatingTable.score.avg())
                ?.toDouble()
                ?: 0.0
        }

    // The difficulty enum is stored by the ordinal
    override suspend fun computeAverageDifficultyByMap(
        snowflake: Snowflake
    ): MindustryMap.Difficulty =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapRatingTable.slice(
                    MindustryMapRatingTable.difficulty, MindustryMapRatingTable.score)
                .select { MindustryMapRatingTable.map eq snowflake }
                .map {
                    it[MindustryMapRatingTable.difficulty].ordinal *
                        it[MindustryMapRatingTable.score]
                }
                .average()
                .let { MindustryMap.Difficulty.entries[it.roundToInt()] }
        }

    override suspend fun deleteMapById(snowflake: Snowflake): Boolean =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapTable.deleteWhere { MindustryMapTable.id eq snowflake } > 0
        }

    override suspend fun createMap(
        name: String,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: InputStream
    ): Snowflake =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            val snowflake = generator.generate()
            MindustryMapTable.insert {
                it[id] = snowflake
                it[MindustryMapTable.name] = name
                it[MindustryMapTable.description] = description
                it[MindustryMapTable.author] = author
                it[MindustryMapTable.width] = width
                it[MindustryMapTable.height] = height
            }
            getMapObject(snowflake).putData(stream)
            snowflake
        }

    override suspend fun updateMap(
        snowflake: Snowflake,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: InputStream
    ) =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapTable.update({ MindustryMapTable.id eq snowflake }) {
                it[MindustryMapTable.description] = description
                it[MindustryMapTable.author] = author
                it[MindustryMapTable.width] = width
                it[MindustryMapTable.height] = height
            }
            getMapObject(snowflake).putData(stream)
        }

    override suspend fun getMapObject(map: Snowflake): StorageBucket.S3Object =
        storage.getObject("maps", "$map.msav")

    override suspend fun searchMapByName(query: String): Flow<MindustryMap> =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapTable.select { MindustryMapTable.name like "%$query%" }
                .asFlow()
                .map { it.toMindustryMap() }
        }

    override suspend fun getMapGamemodes(map: Snowflake): Set<MindustryGamemode> =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapGamemodeTable.select { MindustryMapGamemodeTable.map eq map }
                .asFlow()
                .map { it[MindustryMapGamemodeTable.gamemode] }
                .toSet()
        }

    override suspend fun setMapGamemodes(map: Snowflake, gamemodes: Set<MindustryGamemode>) =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            MindustryMapGamemodeTable.deleteWhere { MindustryMapGamemodeTable.map eq map }
            gamemodes.forEach { mode ->
                MindustryMapGamemodeTable.insert {
                    it[MindustryMapGamemodeTable.map] = map
                    it[gamemode] = mode
                }
            }
        }

    private fun ResultRow.toMindustryMap() =
        MindustryMap(
            snowflake = this[MindustryMapTable.id].value,
            name = this[MindustryMapTable.name],
            description = this[MindustryMapTable.description],
            author = this[MindustryMapTable.author],
            width = this[MindustryMapTable.width],
            height = this[MindustryMapTable.height],
            playtime = this[MindustryMapTable.playtime],
            games = this[MindustryMapTable.games],
            lastUpdate = this[MindustryMapTable.lastUpdate])

    private fun ResultRow.toMindustryMapRating() =
        MindustryMap.Rating(
            user = this[MindustryMapRatingTable.user].value,
            score = this[MindustryMapRatingTable.score],
            difficulty = this[MindustryMapRatingTable.difficulty])
}
