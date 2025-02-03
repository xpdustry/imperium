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
package com.xpdustry.imperium.mindustry.formation

import arc.math.Angles
import arc.math.Mathf
import arc.math.geom.Vec2
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

interface FormationPattern {

    fun calculate(location: Vec2, slot: Int, slots: Int, spacing: Float = 20F, speed: Float = 0.5F)
}

object CircleFormationPattern : FormationPattern {
    override fun calculate(location: Vec2, slot: Int, slots: Int, spacing: Float, speed: Float) {
        if (slots > 1) {
            val angle = (360f * slot) / slots
            val radius = spacing / sin((180f / slots * Mathf.degRad).toDouble()).toFloat()
            location.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius))
        } else {
            location.set(0f, spacing * 1.1f)
        }
    }
}

object SquareFormationPattern : FormationPattern {
    override fun calculate(location: Vec2, slot: Int, slots: Int, spacing: Float, speed: Float) {
        // side of each square of formation
        val side = ceil(sqrt((slots + 1).toFloat())).toInt()
        var cx = slot % side
        var cy = slot / side

        // don't hog the middle spot
        if ((cx == side / 2) && cy == side / 2 && (side % 2) == 1) {
            cx = slots % side
            cy = slots / side
        }

        location.set(cx - (side / 2f - 0.5f), cy - (side / 2f - 0.5f)).scl(spacing * 2F)
    }
}

object RotatingCircleFormationPattern : FormationPattern {
    override fun calculate(location: Vec2, slot: Int, slots: Int, spacing: Float, speed: Float) {
        val cycle = 30000L / speed
        val offset = ((System.currentTimeMillis() % cycle.toLong()) / cycle) * 360f
        val angle = ((360f * slot) / slots) + offset
        val radius = spacing / sin((180f / slots * Mathf.degRad).toDouble()).toFloat()
        location.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius))
    }
}
