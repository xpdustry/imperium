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
package com.xpdustry.imperium.mindustry.misc

import mindustry.gen.Groups
import mindustry.gen.Player

object Entities {
    fun getPlayers(): List<Player> = Groups.player.asList()

    suspend fun getPlayersAsync(): List<Player> = runMindustryThread { Groups.player.asList().toList() }

    fun findPlayerByID(id: Int): Player? = Groups.player.getByID(id)

    suspend fun findPlayerByIDAsync(id: Int): Player? = runMindustryThread { Groups.player.getByID(id) }

    fun getUnits(): List<mindustry.gen.Unit> = Groups.unit.asList()

    suspend fun getUnitsAsync(): List<mindustry.gen.Unit> = runMindustryThread { Groups.unit.asList().toList() }
}
