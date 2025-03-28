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

import mindustry.gen.Unit as MindustryUnit

data class FormationContext(
    var leader: MindustryUnit,
    val members: MutableList<FormationMember> = arrayListOf(),
    val assignments: MutableMap<Int, Int> = hashMapOf(),
    val slots: Int,
    var pattern: FormationPattern = CircleFormationPattern,
    val strategy: SlotAssignmentStrategy = DistanceAssignmentStrategy,
    var deleted: Boolean = false,
    var progress: Float = 0F,
) {
    val spacing: Float
        get() = if (leader.hitSize <= 15) leader.hitSize * 1.6F else leader.hitSize * 1.35F

    val open: Int
        get() = slots - members.size

    fun add(unit: MindustryUnit) {
        val ai = FormationAI(this)
        unit.controller(ai)
        members.add(ai)
    }

    fun remove(member: FormationMember) {
        assignments.remove(member.id)
        members.remove(member)
    }
}
