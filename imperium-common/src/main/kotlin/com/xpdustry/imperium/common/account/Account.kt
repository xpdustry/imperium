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
@file:UseSerializers(JavaDurationSerializer::class, JavaInstantSerializer::class, ObjectIdSerializer::class)

package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.database.Entity
import com.xpdustry.imperium.common.hash.Hash
import com.xpdustry.imperium.common.serialization.JavaDurationSerializer
import com.xpdustry.imperium.common.serialization.JavaInstantSerializer
import com.xpdustry.imperium.common.serialization.ObjectIdSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bson.types.ObjectId
import java.time.Duration
import java.time.Instant

@Serializable
data class Account(
    var username: String,
    var password: Hash,
    var rank: Rank = Rank.NORMAL,
    var verified: Boolean = false,
    val sessions: MutableMap<String, Session> = mutableMapOf(),
    val friends: MutableMap<String, Friend> = mutableMapOf(),
    val achievements: MutableMap<String, Achievement.Progression> = mutableMapOf(),
    var playtime: Duration = Duration.ZERO,
    var games: Int = 0,
    override val _id: ObjectId = ObjectId(),
) : Entity<ObjectId> {
    fun progress(achievement: Achievement) {
        if (completed(achievement)) return
        val progression = achievements.getOrPut(achievement.name.lowercase(), Achievement::Progression)
        progression.progress++
        if (progression.progress >= achievement.goal) {
            progression.completed = true
        }
    }

    fun completed(achievement: Achievement): Boolean {
        return achievements[achievement.name.lowercase()]?.completed == true
    }

    enum class Rank {
        NORMAL,
        OVERSEER,
        MODERATOR,
        ADMINISTRATOR,
        OWNER,
    }

    @Serializable
    data class Session(val expiration: Instant)

    @Serializable
    data class Friend(var pending: Boolean)
}
