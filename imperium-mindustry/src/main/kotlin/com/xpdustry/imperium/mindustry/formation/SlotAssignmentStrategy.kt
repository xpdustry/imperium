// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.formation

import arc.math.Mathf
import arc.math.geom.Vec2
import arc.struct.IntSeq

interface SlotAssignmentStrategy {

    fun update(context: FormationContext)
}

object DistanceAssignmentStrategy : SlotAssignmentStrategy {

    override fun update(context: FormationContext) {
        val slots = IntSeq.range(0, context.members.size)
        val vector = Vec2()

        for (member in context.members) {
            var mindex = 0
            var mcost = Float.MAX_VALUE

            for (i in 0 until slots.size) {
                context.pattern.calculate(context, vector, slots[i])
                val cost = Mathf.dst2(member.targetVector.x, member.targetVector.y, vector.x, vector.y)
                if (cost < mcost) {
                    mcost = cost
                    mindex = i
                }
            }

            context.assignments[member.id] = slots[mindex]
            slots.removeIndex(mindex)
        }
    }
}
