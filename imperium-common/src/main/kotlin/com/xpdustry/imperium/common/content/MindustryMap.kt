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

import java.time.Instant
import kotlin.time.Duration

data class MindustryMap(
    val id: Int,
    val name: String,
    val description: String?,
    val author: String?,
    val width: Int,
    val height: Int,
    val lastUpdate: Instant,
    val gamemodes: Set<MindustryGamemode>,
) {
    enum class Difficulty {
        EASY,
        NORMAL,
        HARD,
        EXPERT,
    }

    data class Rating(val user: Int, val score: Int, val difficulty: Difficulty)

    data class Stats(
        val score: Double,
        val difficulty: Difficulty,
        val games: Int,
        val playtime: Duration,
        val record: Int?,
    )

    data class PlayThrough(val id: Int, val map: Int, val data: Data) {
        data class Data(
            val server: String,
            val start: Instant,
            val playtime: Duration,
            val unitsCreated: Int,
            val ennemiesKilled: Int,
            val wavesLasted: Int,
            val buildingsConstructed: Int,
            val buildingsDeconstructed: Int,
            val buildingsDestroyed: Int,
            val winner: UByte,
        )
    }

    companion object {
        const val MAX_MAP_FILE_SIZE = 4 * 1024 * 1024
        const val MAX_MAP_SIDE_SIZE = 3072
    }
}
