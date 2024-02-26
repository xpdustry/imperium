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
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.exists
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.SnowflakeGenerator
import java.io.InputStream
import java.time.Instant
import java.util.function.Supplier
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Join
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.update

// TODO Implement Pagination ?
interface MindustryMapManager {

    suspend fun findMapBySnowflake(snowflake: Snowflake): MindustryMap?

    suspend fun findMapByName(name: String): MindustryMap?

    suspend fun findAllMapsByGamemode(gamemode: MindustryGamemode): List<MindustryMap>

    suspend fun findRatingByMapAndUser(map: Snowflake, user: Snowflake): MindustryMap.Rating?

    suspend fun findAllMaps(): List<MindustryMap>

    suspend fun deleteMapBySnowflake(snowflake: Snowflake): Boolean

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

    // TODO This is horrible, pass a Params class instead
    suspend fun addMapGame(
        map: Snowflake,
        start: Instant,
        playtime: Duration,
        unitsCreated: Int,
        ennemiesKilled: Int,
        wavesLasted: Int,
        buildingsConstructed: Int,
        buildingsDeconstructed: Int,
        buildingsDestroyed: Int,
        winner: UByte
    )

    suspend fun findMapGameBySnowflake(game: Snowflake): MindustryMap.Game?

    suspend fun getMapStats(map: Snowflake): MindustryMap.Stats?

    suspend fun getMapInputStream(map: Snowflake): InputStream?

    suspend fun searchMapByName(query: String): List<MindustryMap>

    suspend fun setMapGamemodes(map: Snowflake, gamemodes: Set<MindustryGamemode>): Boolean
}

class SimpleMindustryMapManager(
    private val provider: SQLProvider,
    private val generator: SnowflakeGenerator,
    private val config: ImperiumConfig,
    private val messenger: Messenger
) : MindustryMapManager, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction {
            SchemaUtils.create(
                MindustryMapTable,
                MindustryMapRatingTable,
                MindustryMapGamemodeTable,
                MindustryMapGameTable)
        }
    }

    override suspend fun findMapBySnowflake(snowflake: Snowflake): MindustryMap? =
        provider.newSuspendTransaction {
            MindustryMapTable.selectAllWithoutFile()
                .where { MindustryMapTable.id eq snowflake }
                .firstOrNull()
                ?.toMindustryMap()
        }

    override suspend fun findMapByName(name: String): MindustryMap? =
        provider.newSuspendTransaction {
            MindustryMapTable.selectAllWithoutFile()
                .where { MindustryMapTable.name eq name }
                .firstOrNull()
                ?.toMindustryMap()
        }

    override suspend fun findAllMapsByGamemode(gamemode: MindustryGamemode): List<MindustryMap> =
        provider.newSuspendTransaction {
            (MindustryMapTable leftJoin MindustryMapGamemodeTable)
                .selectAllWithoutFile()
                .where { (MindustryMapGamemodeTable.gamemode eq gamemode) }
                .map { it.toMindustryMap() }
        }

    override suspend fun findRatingByMapAndUser(
        map: Snowflake,
        user: Snowflake
    ): MindustryMap.Rating? =
        provider.newSuspendTransaction {
            MindustryMapRatingTable.selectAll()
                .where {
                    (MindustryMapRatingTable.map eq map) and (MindustryMapRatingTable.user eq user)
                }
                .firstOrNull()
                ?.toMindustryMapRating()
        }

    override suspend fun findAllMaps(): List<MindustryMap> =
        provider.newSuspendTransaction {
            MindustryMapTable.selectAllWithoutFile().map { it.toMindustryMap() }
        }

    override suspend fun deleteMapBySnowflake(snowflake: Snowflake): Boolean =
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

    override suspend fun addMapGame(
        map: Snowflake,
        start: Instant,
        playtime: Duration,
        unitsCreated: Int,
        ennemiesKilled: Int,
        wavesLasted: Int,
        buildingsConstructed: Int,
        buildingsDeconstructed: Int,
        buildingsDestroyed: Int,
        winner: UByte
    ): Unit =
        provider.newSuspendTransaction {
            val snowflake = generator.generate()
            MindustryMapGameTable.insert {
                it[MindustryMapGameTable.id] = snowflake
                it[MindustryMapGameTable.map] = map
                it[server] = config.server.name
                it[MindustryMapGameTable.start] = start
                it[MindustryMapGameTable.playtime] = playtime.toJavaDuration()
                it[MindustryMapGameTable.unitsCreated] = unitsCreated
                it[MindustryMapGameTable.ennemiesKilled] = ennemiesKilled
                it[MindustryMapGameTable.wavesLasted] = wavesLasted
                it[MindustryMapGameTable.buildingsConstructed] = buildingsConstructed
                it[MindustryMapGameTable.buildingsDeconstructed] = buildingsDeconstructed
                it[MindustryMapGameTable.buildingsDestroyed] = buildingsDestroyed
                it[MindustryMapGameTable.winner] = winner
            }
        }

    override suspend fun findMapGameBySnowflake(game: Snowflake): MindustryMap.Game? =
        provider.newSuspendTransaction {
            MindustryMapGameTable.selectAll()
                .where { MindustryMapGameTable.id eq game }
                .firstOrNull()
                ?.toMindustryMapGame()
        }

    override suspend fun getMapStats(map: Snowflake): MindustryMap.Stats? =
        provider.newSuspendTransaction {
            if (!MindustryMapTable.exists { MindustryMapTable.id eq map }) {
                return@newSuspendTransaction null
            }
            val score =
                MindustryMapRatingTable.select(MindustryMapRatingTable.score.avg())
                    .where { MindustryMapRatingTable.map eq map }
                    .firstOrNull()
                    ?.get(MindustryMapRatingTable.score.avg())
                    ?.toDouble()
                    ?: 2.5
            val difficulty =
                MindustryMapRatingTable.select(
                        MindustryMapRatingTable.difficulty, MindustryMapRatingTable.score)
                    .where { MindustryMapRatingTable.map eq map }
                    .map {
                        it[MindustryMapRatingTable.difficulty].ordinal *
                            it[MindustryMapRatingTable.score]
                    }
                    .average()
                    .let {
                        if (it.isNaN()) MindustryMap.Difficulty.NORMAL
                        else MindustryMap.Difficulty.entries[it.roundToInt()]
                    }
            val games =
                MindustryMapGameTable.selectAll()
                    .where { MindustryMapGameTable.map eq map }
                    .count()
                    .toInt()
            val playtime =
                MindustryMapGameTable.select(MindustryMapGameTable.playtime.sum())
                    .where { MindustryMapGameTable.map eq map }
                    .firstOrNull()
                    ?.get(MindustryMapGameTable.playtime.sum())
                    ?.toKotlinDuration()
                    ?: Duration.ZERO
            val record =
                MindustryMapGameTable.select(MindustryMapGameTable.id)
                    .where { MindustryMapGameTable.map eq map }
                    .orderBy(MindustryMapGameTable.playtime, SortOrder.DESC)
                    .firstOrNull()
                    ?.get(MindustryMapGameTable.id)
                    ?.value

            MindustryMap.Stats(score, difficulty, games, playtime, record)
        }

    override suspend fun getMapInputStream(map: Snowflake): InputStream? =
        provider.newSuspendTransaction {
            MindustryMapTable.select(MindustryMapTable.file)
                .where { MindustryMapTable.id eq map }
                .firstOrNull()
                ?.get(MindustryMapTable.file)
                ?.inputStream
        }

    override suspend fun searchMapByName(query: String): List<MindustryMap> =
        provider.newSuspendTransaction {
            MindustryMapTable.selectAllWithoutFile()
                .where { MindustryMapTable.name like "%$query%" }
                .map { it.toMindustryMap() }
        }

    override suspend fun setMapGamemodes(
        map: Snowflake,
        gamemodes: Set<MindustryGamemode>
    ): Boolean {
        val previous = findMapBySnowflake(map)?.gamemodes ?: return false
        provider.newSuspendTransaction {
            MindustryMapGamemodeTable.deleteWhere { MindustryMapGamemodeTable.map eq map }
            MindustryMapGamemodeTable.batchUpsert(gamemodes) {
                this[MindustryMapGamemodeTable.map] = map
                this[MindustryMapGamemodeTable.gamemode] = it
            }
        }
        messenger.publish(MapReloadMessage(gamemodes + previous))
        return true
    }

    private fun ColumnSet.selectAllWithoutFile() =
        select((if (this is Join) table.columns else columns) - MindustryMapTable.file)

    private suspend fun getMapGamemodes(map: Snowflake): Set<MindustryGamemode> =
        provider.newSuspendTransaction {
            MindustryMapGamemodeTable.select(MindustryMapGamemodeTable.gamemode)
                .where { MindustryMapGamemodeTable.map eq map }
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
            lastUpdate = this[MindustryMapTable.lastUpdate],
            gamemodes = getMapGamemodes(this[MindustryMapTable.id].value))

    private fun ResultRow.toMindustryMapRating() =
        MindustryMap.Rating(
            user = this[MindustryMapRatingTable.user].value,
            score = this[MindustryMapRatingTable.score],
            difficulty = this[MindustryMapRatingTable.difficulty])

    private fun ResultRow.toMindustryMapGame() =
        MindustryMap.Game(
            snowflake = this[MindustryMapGameTable.id].value,
            map = this[MindustryMapGameTable.map].value,
            server = this[MindustryMapGameTable.server],
            start = this[MindustryMapGameTable.start],
            playtime = this[MindustryMapGameTable.playtime].toKotlinDuration(),
            unitsCreated = this[MindustryMapGameTable.unitsCreated],
            ennemiesKilled = this[MindustryMapGameTable.ennemiesKilled],
            wavesLasted = this[MindustryMapGameTable.wavesLasted],
            buildingsConstructed = this[MindustryMapGameTable.buildingsConstructed],
            buildingsDeconstructed = this[MindustryMapGameTable.buildingsDeconstructed],
            buildingsDestroyed = this[MindustryMapGameTable.buildingsDestroyed],
            winner = this[MindustryMapGameTable.winner])
}
