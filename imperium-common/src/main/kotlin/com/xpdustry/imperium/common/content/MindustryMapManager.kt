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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.misc.exists
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.SnowflakeGenerator
import java.io.InputStream
import java.util.function.Supplier
import kotlin.math.roundToInt
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Join
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.update

// TODO Implement Pagination ?
interface MindustryMapManager {

    suspend fun findMapBySnowflake(snowflake: Snowflake): MindustryMap?

    suspend fun findMapByName(name: String): MindustryMap?

    suspend fun findAllMapsByGamemode(gamemode: MindustryGamemode): List<MindustryMap>

    suspend fun findRatingByMapAndUser(map: Snowflake, user: Snowflake): MindustryMap.Rating?

    suspend fun findAllMaps(): List<MindustryMap>

    suspend fun computeAverageScoreByMap(snowflake: Snowflake): Double

    suspend fun computeAverageDifficultyByMap(snowflake: Snowflake): MindustryMap.Difficulty

    suspend fun deleteMapById(snowflake: Snowflake): Boolean

    suspend fun createMap(
        name: String,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: Supplier<InputStream>
    ): Snowflake

    suspend fun updateMap(
        snowflake: Snowflake,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: Supplier<InputStream>
    ): Boolean

    suspend fun getMapInputStream(map: Snowflake): InputStream?

    suspend fun searchMapByName(query: String): List<MindustryMap>

    suspend fun setMapGamemodes(map: Snowflake, gamemodes: Set<MindustryGamemode>): Boolean
}

class SimpleMindustryMapManager(
    private val provider: SQLProvider,
    private val generator: SnowflakeGenerator
) : MindustryMapManager, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction {
            SchemaUtils.create(
                MindustryMapTable, MindustryMapRatingTable, MindustryMapGamemodeTable)
        }
    }

    override suspend fun findMapBySnowflake(snowflake: Snowflake): MindustryMap? =
        provider.newSuspendTransaction {
            MindustryMapTable.sliceWithoutFile()
                .select { MindustryMapTable.id eq snowflake }
                .firstOrNull()
                ?.toMindustryMap()
        }

    override suspend fun findMapByName(name: String): MindustryMap? =
        provider.newSuspendTransaction {
            MindustryMapTable.sliceWithoutFile()
                .select { MindustryMapTable.name eq name }
                .firstOrNull()
                ?.toMindustryMap()
        }

    override suspend fun findAllMapsByGamemode(gamemode: MindustryGamemode): List<MindustryMap> =
        provider.newSuspendTransaction {
            (MindustryMapTable leftJoin MindustryMapGamemodeTable)
                .sliceWithoutFile()
                .select { (MindustryMapGamemodeTable.gamemode eq gamemode) }
                .map { it.toMindustryMap() }
        }

    override suspend fun findRatingByMapAndUser(
        map: Snowflake,
        user: Snowflake
    ): MindustryMap.Rating? =
        provider.newSuspendTransaction {
            MindustryMapRatingTable.select {
                    (MindustryMapRatingTable.map eq map) and (MindustryMapRatingTable.user eq user)
                }
                .firstOrNull()
                ?.toMindustryMapRating()
        }

    override suspend fun findAllMaps(): List<MindustryMap> =
        provider.newSuspendTransaction {
            MindustryMapTable.sliceWithoutFile().selectAll().map { it.toMindustryMap() }
        }

    override suspend fun computeAverageScoreByMap(snowflake: Snowflake): Double =
        provider.newSuspendTransaction {
            MindustryMapRatingTable.slice(MindustryMapRatingTable.score.avg())
                .select { MindustryMapRatingTable.map eq snowflake }
                .firstOrNull()
                ?.get(MindustryMapRatingTable.score.avg())
                ?.toDouble()
                ?: 2.5
        }

    // The difficulty enum is stored by the ordinal
    override suspend fun computeAverageDifficultyByMap(
        snowflake: Snowflake
    ): MindustryMap.Difficulty =
        provider.newSuspendTransaction {
            MindustryMapRatingTable.slice(
                    MindustryMapRatingTable.difficulty, MindustryMapRatingTable.score)
                .select { MindustryMapRatingTable.map eq snowflake }
                .map {
                    it[MindustryMapRatingTable.difficulty].ordinal *
                        it[MindustryMapRatingTable.score]
                }
                .average()
                .let {
                    if (it.isNaN()) MindustryMap.Difficulty.NORMAL
                    else MindustryMap.Difficulty.entries[it.roundToInt()]
                }
        }

    override suspend fun deleteMapById(snowflake: Snowflake): Boolean =
        provider.newSuspendTransaction {
            MindustryMapTable.deleteWhere { MindustryMapTable.id eq snowflake } > 0
        }

    override suspend fun createMap(
        name: String,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: Supplier<InputStream>
    ): Snowflake =
        provider.newSuspendTransaction {
            val snowflake = generator.generate()
            MindustryMapTable.insert {
                it[id] = snowflake
                it[MindustryMapTable.name] = name
                it[MindustryMapTable.description] = description
                it[MindustryMapTable.author] = author
                it[MindustryMapTable.width] = width
                it[MindustryMapTable.height] = height
                it[file] = ExposedBlob(stream.get().use(InputStream::readAllBytes))
            }
            snowflake
        }

    override suspend fun updateMap(
        snowflake: Snowflake,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: Supplier<InputStream>
    ): Boolean =
        provider.newSuspendTransaction {
            val rows =
                MindustryMapTable.update({ MindustryMapTable.id eq snowflake }) {
                    it[MindustryMapTable.description] = description
                    it[MindustryMapTable.author] = author
                    it[MindustryMapTable.width] = width
                    it[MindustryMapTable.height] = height
                    it[file] = ExposedBlob(stream.get().use(InputStream::readAllBytes))
                }
            rows != 0
        }

    override suspend fun getMapInputStream(map: Snowflake): InputStream? =
        provider.newSuspendTransaction {
            MindustryMapTable.slice(MindustryMapTable.file)
                .select { MindustryMapTable.id eq map }
                .firstOrNull()
                ?.get(MindustryMapTable.file)
                ?.inputStream
        }

    override suspend fun searchMapByName(query: String): List<MindustryMap> =
        provider.newSuspendTransaction {
            MindustryMapTable.sliceWithoutFile()
                .select { MindustryMapTable.name like "%$query%" }
                .map { it.toMindustryMap() }
        }

    override suspend fun setMapGamemodes(
        map: Snowflake,
        gamemodes: Set<MindustryGamemode>
    ): Boolean =
        provider.newSuspendTransaction {
            if (!MindustryMapTable.exists { MindustryMapTable.id eq map }) {
                return@newSuspendTransaction false
            }
            MindustryMapGamemodeTable.deleteWhere { MindustryMapGamemodeTable.map eq map }
            MindustryMapGamemodeTable.batchUpsert(gamemodes) {
                this[MindustryMapGamemodeTable.map] = map
                this[MindustryMapGamemodeTable.gamemode] = it
            }
            true
        }

    private fun ColumnSet.sliceWithoutFile() =
        slice((if (this is Join) table.columns else columns) - MindustryMapTable.file)

    private suspend fun getMapGamemodes(map: Snowflake): Set<MindustryGamemode> =
        provider.newSuspendTransaction {
            MindustryMapGamemodeTable.slice(MindustryMapGamemodeTable.gamemode)
                .select { MindustryMapGamemodeTable.map eq map }
                .mapTo(mutableSetOf()) { it[MindustryMapGamemodeTable.gamemode] }
        }

    private suspend fun ResultRow.toMindustryMap() =
        MindustryMap(
            snowflake = this[MindustryMapTable.id].value,
            name = this[MindustryMapTable.name],
            description = this[MindustryMapTable.description],
            author = this[MindustryMapTable.author],
            width = this[MindustryMapTable.width],
            height = this[MindustryMapTable.height],
            playtime = this[MindustryMapTable.playtime],
            games = this[MindustryMapTable.games],
            lastUpdate = this[MindustryMapTable.lastUpdate],
            gamemodes = getMapGamemodes(this[MindustryMapTable.id].value))

    private fun ResultRow.toMindustryMapRating() =
        MindustryMap.Rating(
            user = this[MindustryMapRatingTable.user].value,
            score = this[MindustryMapRatingTable.score],
            difficulty = this[MindustryMapRatingTable.difficulty])
}
