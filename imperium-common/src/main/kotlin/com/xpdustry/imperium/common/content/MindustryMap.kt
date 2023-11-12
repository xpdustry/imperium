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

import com.xpdustry.imperium.common.database.Entity
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.serialization.SerializableJDuration
import com.xpdustry.imperium.common.serialization.SerializableJInstant
import com.xpdustry.imperium.common.serialization.SerializableObjectId
import java.time.Duration
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class MindustryMap(
    override val _id: SerializableObjectId = ObjectId(),
    var name: String,
    var description: String?,
    var author: String?,
    var width: Int,
    var height: Int,
    var playtime: SerializableJDuration = Duration.ZERO,
    var games: Int = 0,
    var gamemodes: MutableSet<MindustryGamemode> = mutableSetOf(),
    var lastUpdate: SerializableJInstant = _id.date.toInstant(),
) : Entity<ObjectId>

@Serializable
data class Rating(
    override val _id: SerializableObjectId = ObjectId(),
    var map: SerializableObjectId,
    var player: MindustryUUID,
    var score: Int,
    var difficulty: Difficulty,
) : Entity<ObjectId> {
    enum class Difficulty {
        EASY,
        NORMAL,
        HARD,
        EXPERT
    }
}
