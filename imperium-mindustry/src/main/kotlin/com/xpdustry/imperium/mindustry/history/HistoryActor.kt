// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.history

import com.xpdustry.imperium.common.misc.MindustryUUID
import mindustry.game.Team
import mindustry.gen.Player
import mindustry.gen.Unit as MindustryUnit
import mindustry.type.UnitType

data class HistoryActor(val player: MindustryUUID?, val team: Team, val unit: UnitType?) {
    constructor(unit: MindustryUnit) : this(unit.player?.uuid(), unit.team(), unit.type)

    constructor(player: Player) : this(player.uuid(), player.team(), player.unit().type)
}
