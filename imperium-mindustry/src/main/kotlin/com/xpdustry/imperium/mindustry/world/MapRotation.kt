// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.world

import kotlin.math.pow
import kotlin.random.Random

/** Higher values make the rotation stricter, 2 is close to a round-robin while still feeling random. */
private const val RECENCY_EXPONENT = 2.0

/**
 * How many games ago each map of the [pool] was played, [recent] being the latest played maps, most recent first. Maps
 * missing from [recent] or played more than a full cycle ago are capped to the pool size.
 */
internal fun gamesSinceLastPlayed(pool: List<Int>, recent: List<Int>): Map<Int, Int> = pool.associateWith { map ->
    val index = recent.indexOf(map)
    if (index == -1) pool.size else minOf(index, pool.size)
}

/** Picks the next map of the [pool], favoring the ones that haven't been played for a while. */
internal fun pickNextMap(pool: List<Int>, recent: List<Int>, random: Random = Random): Int? {
    val maps = pool.distinct()
    if (maps.size <= 1) return maps.firstOrNull()

    val weights = gamesSinceLastPlayed(maps, recent).mapValues { (_, games) -> games.toDouble().pow(RECENCY_EXPONENT) }

    // Lay the weights end to end, the picked map is the first one whose slice ends past a random point
    val ends = weights.values.runningReduce(Double::plus)
    val point = random.nextDouble(ends.last())
    return weights.keys.elementAt(ends.indexOfFirst { point < it })
}
