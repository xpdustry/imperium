// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.formation

import arc.math.Angles
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.util.Time
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

interface FormationPattern {

    fun calculate(context: FormationContext, target: Vec2, slot: Int)
}

object CircleFormationPattern : FormationPattern {
    override fun calculate(context: FormationContext, target: Vec2, slot: Int) {
        if (context.slots > 1) {
            val angle = (360f * slot) / context.slots
            val radius = context.spacing / sin((180f / context.slots * Mathf.degRad).toDouble()).toFloat()
            target.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius))
        } else {
            target.set(0f, context.spacing * 1.1f)
        }
    }
}

object SquareFormationPattern : FormationPattern {
    override fun calculate(context: FormationContext, target: Vec2, slot: Int) {
        // side of each square of formation
        val side = ceil(sqrt((context.slots + 1).toFloat())).toInt()
        var cx = slot % side
        var cy = slot / side

        // don't hog the middle spot
        if ((cx == side / 2) && cy == side / 2 && (side % 2) == 1) {
            cx = context.slots % side
            cy = context.slots / side
        }

        target.set(cx - (side / 2f - 0.5f), cy - (side / 2f - 0.5f)).scl(context.spacing * 2F)
    }
}

// Hug the player for tightest formation
object CompactFormationPattern : FormationPattern {
    override fun calculate(context: FormationContext, target: Vec2, slot: Int) {
        if (context.slots > 1) {
            val angle = (360f * slot) / context.slots
            val radius = context.spacing
            target.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius))
        } else {
            target.set(0f, context.spacing)
        }
    }
}

object RotatingCircleFormationPattern : FormationPattern {
    override fun calculate(context: FormationContext, target: Vec2, slot: Int) {
        val cycle = (30 * Time.toSeconds) / context.leader.speed()
        val offset = ((context.progress.toLong() % cycle.toLong()) / cycle) * 360f
        val angle = ((360f * slot) / context.slots) + offset
        val radius = context.spacing / sin((180f / context.slots * Mathf.degRad).toDouble()).toFloat()
        target.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius))
    }
}
