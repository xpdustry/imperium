// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.content

import kotlin.time.Duration
import kotlin.time.Instant

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
