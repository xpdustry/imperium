// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.formation

import arc.math.geom.Vec2
import mindustry.gen.Unit as MindustryUnit

interface FormationMember {
    val targetVector: Vec2
    val id: Int
    // Goofy name due to FormationAI override
    val backingUnit: MindustryUnit

    fun isValid(): Boolean
}
