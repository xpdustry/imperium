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
package com.xpdustry.imperium.mindustry.history

import com.xpdustry.imperium.common.misc.MindustryUUID
import mindustry.game.Team
import mindustry.gen.Player
import mindustry.gen.Unit as MindustryUnit
import mindustry.type.UnitType

data class HistoryActor(val player: MindustryUUID?, val team: Team, val unit: UnitType) {
    constructor(unit: MindustryUnit) : this(unit.player?.uuid(), unit.team(), unit.type)

    constructor(player: Player) : this(player.uuid(), player.team(), player.unit().type)
}
