/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
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
import com.xpdustry.imperium.common.storage.StorageBucket
import java.io.InputStream
import java.time.Instant
import java.util.function.Supplier
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

interface MindustryMapManager {

    suspend fun findMapById(id: Int): MindustryMap?

    suspend fun findMapByName(name: String): MindustryMap?

    suspend fun findAllMapsByGamemode(gamemode: MindustryGamemode): List<MindustryMap>

    suspend fun findRatingByMapAndUser(map: Int, user: Int): MindustryMap.Rating?

    suspend fun saveRating(map: Int, user: Int, score: Int, difficulty: MindustryMap.Difficulty)

    suspend fun findAllMaps(): List<MindustryMap>

    suspend fun deleteMapById(id: Int): Boolean

    suspend fun createMap(
        name: String,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: Supplier<InputStream>,
    ): Int

    suspend fun updateMap(
        id: Int,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: Supplier<InputStream>,
    ): Boolean

    suspend fun addMapGame(map: Int, data: MindustryMap.PlayThrough.Data)

    suspend fun findMapGameBySnowflake(game: Int): MindustryMap.PlayThrough?

    suspend fun getMapStats(map: Int): MindustryMap.Stats?

    suspend fun getMapInputStream(map: Int): InputStream?

    suspend fun searchMapByName(query: String): List<MindustryMap>

    suspend fun setMapGamemodes(map: Int, gamemodes: Set<MindustryGamemode>): Boolean
}

class SimpleMindustryMapManager(
    private val provider: SQLProvider,
    private val config: ImperiumConfig,
    private val messenger: Messenger,
    private val storage: StorageBucket,
) : MindustryMapManager, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction {
            SchemaUtils.create(
                MindustryMapTable,
                MindustryMapRatingTable,
                MindustryMapGamemodeTable,
                MindustryMapGameTable,
            )
        }
    }

    override suspend fun findMapById(id: Int): MindustryMap? =
        provider.newSuspendTransaction {
            MindustryMapTable.selectAll().where { MindustryMapTable.id eq id }.firstOrNull()?.toMindustryMap()
        }

    override suspend fun findMapByName(name: String): MindustryMap? =
        provider.newSuspendTransaction {
            MindustryMapTable.selectAll().where { MindustryMapTable.name eq name }.firstOrNull()?.toMindustryMap()
        }

    override suspend fun findAllMapsByGamemode(gamemode: MindustryGamemode): List<MindustryMap> =
        provider.newSuspendTransaction {
            (MindustryMapTable leftJoin MindustryMapGamemodeTable)
                .selectAll()
                .where { (MindustryMapGamemodeTable.gamemode eq gamemode) }
                .map { it.toMindustryMap() }
        }

    override suspend fun findRatingByMapAndUser(map: Int, user: Int): MindustryMap.Rating? =
        provider.newSuspendTransaction {
            MindustryMapRatingTable.selectAll()
                .where { (MindustryMapRatingTable.map eq map) and (MindustryMapRatingTable.user eq user) }
                .firstOrNull()
                ?.toMindustryMapRating()
        }

    override suspend fun saveRating(map: Int, user: Int, score: Int, difficulty: MindustryMap.Difficulty) {
        provider.newSuspendTransaction {
            MindustryMapRatingTable.upsert {
                it[MindustryMapRatingTable.map] = map
                it[MindustryMapRatingTable.user] = user
                it[MindustryMapRatingTable.score] = score
                it[MindustryMapRatingTable.difficulty] = difficulty
            }
        }
    }

    override suspend fun findAllMaps(): List<MindustryMap> =
        provider.newSuspendTransaction { MindustryMapTable.selectAll().map { it.toMindustryMap() } }

    override suspend fun deleteMapById(id: Int): Boolean =
        provider.newSuspendTransaction { MindustryMapTable.deleteWhere { MindustryMapTable.id eq id } > 0 }

    override suspend fun createMap(
        name: String,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: Supplier<InputStream>,
    ): Int =
        provider.newSuspendTransaction {
            val id =
                MindustryMapTable.insertAndGetId {
                    it[MindustryMapTable.name] = name
                    it[MindustryMapTable.description] = description
                    it[MindustryMapTable.author] = author
                    it[MindustryMapTable.width] = width
                    it[MindustryMapTable.height] = height
                }
            stream.get().use { storage.getObject("maps", "$id.msav").putData(it) }
            id.value
        }

    override suspend fun updateMap(
        id: Int,
        description: String?,
        author: String?,
        width: Int,
        height: Int,
        stream: Supplier<InputStream>,
    ): Boolean =
        provider.newSuspendTransaction {
            val rows =
                MindustryMapTable.update({ MindustryMapTable.id eq id }) {
                    it[MindustryMapTable.description] = description
                    it[MindustryMapTable.author] = author
                    it[MindustryMapTable.width] = width
                    it[MindustryMapTable.height] = height
                    it[lastUpdate] = Instant.now()
                }
            stream.get().use { storage.getObject("maps", "$id.msav").putData(it) }
            if (rows != 0) {
                messenger.publish(MapReloadMessage(getMapGamemodes(id)))
                return@newSuspendTransaction true
            } else {
                return@newSuspendTransaction false
            }
        }

    override suspend fun addMapGame(map: Int, data: MindustryMap.PlayThrough.Data): Unit =
        provider.newSuspendTransaction {
            MindustryMapGameTable.insert {
                it[MindustryMapGameTable.map] = map
                it[server] = data.server
                it[start] = data.start
                it[playtime] = data.playtime.toJavaDuration()
                it[unitsCreated] = data.unitsCreated
                it[ennemiesKilled] = data.ennemiesKilled
                it[wavesLasted] = data.wavesLasted
                it[buildingsConstructed] = data.buildingsConstructed
                it[buildingsDeconstructed] = data.buildingsDeconstructed
                it[buildingsDestroyed] = data.buildingsDestroyed
                it[winner] = data.winner
            }
        }

    override suspend fun findMapGameBySnowflake(game: Int): MindustryMap.PlayThrough? =
        provider.newSuspendTransaction {
            MindustryMapGameTable.selectAll()
                .where { MindustryMapGameTable.id eq game }
                .firstOrNull()
                ?.toMindustryMapGame()
        }

    override suspend fun getMapStats(map: Int): MindustryMap.Stats? =
        provider.newSuspendTransaction {
            if (!MindustryMapTable.exists { MindustryMapTable.id eq map }) {
                return@newSuspendTransaction null
            }
            val score =
                MindustryMapRatingTable.select(MindustryMapRatingTable.score.avg())
                    .where { MindustryMapRatingTable.map eq map }
                    .firstOrNull()
                    ?.get(MindustryMapRatingTable.score.avg())
                    ?.toDouble() ?: 2.5
            val difficulty =
                MindustryMapRatingTable.select(MindustryMapRatingTable.difficulty.avg())
                    .where { MindustryMapRatingTable.map eq map }
                    .map { it[MindustryMapRatingTable.difficulty.avg()] }
                    .first()
                    .let {
                        if (it == null) MindustryMap.Difficulty.NORMAL
                        else MindustryMap.Difficulty.entries[it.toDouble().roundToInt()]
                    }
            val games = MindustryMapGameTable.selectAll().where { MindustryMapGameTable.map eq map }.count().toInt()
            val playtime =
                MindustryMapGameTable.select(MindustryMapGameTable.playtime.sum())
                    .where { MindustryMapGameTable.map eq map }
                    .firstOrNull()
                    ?.get(MindustryMapGameTable.playtime.sum())
                    ?.toKotlinDuration() ?: Duration.ZERO
            val record =
                MindustryMapGameTable.select(MindustryMapGameTable.id)
                    .where { MindustryMapGameTable.map eq map }
                    .orderBy(MindustryMapGameTable.playtime, SortOrder.DESC)
                    .firstOrNull()
                    ?.get(MindustryMapGameTable.id)
                    ?.value

            MindustryMap.Stats(score, difficulty, games, playtime, record)
        }

    override suspend fun getMapInputStream(map: Int): InputStream? =
        storage.getObject("maps", "$map.msav").takeIf { it.exists }?.getData()

    override suspend fun searchMapByName(query: String): List<MindustryMap> =
        provider.newSuspendTransaction {
            MindustryMapTable.selectAll().where { MindustryMapTable.name like "%$query%" }.map { it.toMindustryMap() }
        }

    override suspend fun setMapGamemodes(map: Int, gamemodes: Set<MindustryGamemode>): Boolean {
        val previous = findMapById(map)?.gamemodes ?: return false
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

    private suspend fun getMapGamemodes(map: Int): Set<MindustryGamemode> =
        provider.newSuspendTransaction {
            MindustryMapGamemodeTable.select(MindustryMapGamemodeTable.gamemode)
                .where { MindustryMapGamemodeTable.map eq map }
                .mapTo(mutableSetOf()) { it[MindustryMapGamemodeTable.gamemode] }
        }

    private suspend fun ResultRow.toMindustryMap() =
        MindustryMap(
            id = this[MindustryMapTable.id].value,
            name = this[MindustryMapTable.name],
            description = this[MindustryMapTable.description],
            author = this[MindustryMapTable.author],
            width = this[MindustryMapTable.width],
            height = this[MindustryMapTable.height],
            lastUpdate = this[MindustryMapTable.lastUpdate],
            gamemodes = getMapGamemodes(this[MindustryMapTable.id].value),
        )

    private fun ResultRow.toMindustryMapRating() =
        MindustryMap.Rating(
            user = this[MindustryMapRatingTable.user].value,
            score = this[MindustryMapRatingTable.score],
            difficulty = this[MindustryMapRatingTable.difficulty],
        )

    private fun ResultRow.toMindustryMapGame() =
        MindustryMap.PlayThrough(
            id = this[MindustryMapGameTable.id].value,
            map = this[MindustryMapGameTable.map].value,
            data =
                MindustryMap.PlayThrough.Data(
                    server = this[MindustryMapGameTable.server],
                    start = this[MindustryMapGameTable.start],
                    playtime = this[MindustryMapGameTable.playtime].toKotlinDuration(),
                    unitsCreated = this[MindustryMapGameTable.unitsCreated],
                    ennemiesKilled = this[MindustryMapGameTable.ennemiesKilled],
                    wavesLasted = this[MindustryMapGameTable.wavesLasted],
                    buildingsConstructed = this[MindustryMapGameTable.buildingsConstructed],
                    buildingsDeconstructed = this[MindustryMapGameTable.buildingsDeconstructed],
                    buildingsDestroyed = this[MindustryMapGameTable.buildingsDestroyed],
                    winner = this[MindustryMapGameTable.winner],
                ),
        )
}
