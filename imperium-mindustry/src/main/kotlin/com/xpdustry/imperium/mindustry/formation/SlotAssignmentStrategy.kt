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
                context.pattern.calculate(vector, slots[i], context.slots)
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
