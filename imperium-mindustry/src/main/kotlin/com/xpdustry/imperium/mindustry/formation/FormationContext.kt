// SPDX-License-Identifier: GPL-3.0-only
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
