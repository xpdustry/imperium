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
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.database.Entity
import com.xpdustry.imperium.common.hash.Hash
import com.xpdustry.imperium.common.serialization.SerializableJDuration
import com.xpdustry.imperium.common.serialization.SerializableJInstant
import com.xpdustry.imperium.common.serialization.SerializableObjectId
import java.time.Duration
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Account(
    var username: String,
    var password: Hash,
    var discord: Long? = null,
    val roles: MutableSet<Role> = mutableSetOf(),
    val sessions: MutableMap<String, Session> = mutableMapOf(),
    val achievements: MutableMap<String, Achievement.Progression> = mutableMapOf(),
    var playtime: SerializableJDuration = Duration.ZERO,
    var games: Int = 0,
    override val _id: SerializableObjectId = ObjectId(),
) : Entity<ObjectId> {

    val verified: Boolean
        get() = discord != null || roles.containsRole(Role.VERIFIED)

    fun progress(achievement: Achievement, value: Int = 1) {
        if (completed(achievement)) return
        val progression =
            achievements.getOrPut(achievement.name.lowercase(), Achievement::Progression)
        progression.progress += value
        if (progression.progress >= achievement.goal) {
            progression.completed = true
        }
    }

    fun completed(achievement: Achievement): Boolean =
        achievements[achievement.name.lowercase()]?.completed == true

    @Serializable data class Session(val expiration: SerializableJInstant)
}
