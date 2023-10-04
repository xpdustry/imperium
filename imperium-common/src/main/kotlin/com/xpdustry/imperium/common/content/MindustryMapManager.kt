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

import com.xpdustry.imperium.common.account.MindustryUUID
import com.xpdustry.imperium.common.storage.S3Object
import kotlinx.coroutines.flow.Flow
import org.bson.types.ObjectId
import java.io.InputStream

interface MindustryMapManager {
    suspend fun findMapById(id: ObjectId): MindustryMap?
    suspend fun findMapByName(name: String): MindustryMap?
    suspend fun findMaps(server: String? = null): Flow<MindustryMap>
    suspend fun findMapsByServer(server: String): Flow<MindustryMap>
    suspend fun findRatingByMapAndPlayer(map: ObjectId, player: MindustryUUID): Rating?
    suspend fun computeAverageScoreByMap(map: ObjectId): Double
    suspend fun computeAverageDifficultyByMap(map: ObjectId): Rating.Difficulty
    suspend fun deleteMapById(id: ObjectId): Boolean
    suspend fun saveMap(map: MindustryMap, stream: InputStream)
    suspend fun getMapObject(map: ObjectId): S3Object?
    suspend fun updateMapById(id: ObjectId, updater: suspend MindustryMap.() -> Unit)
}
