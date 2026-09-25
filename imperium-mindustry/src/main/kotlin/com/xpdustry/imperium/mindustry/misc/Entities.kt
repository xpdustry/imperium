// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.misc

import mindustry.gen.Groups
import mindustry.gen.Player

object Entities {
    fun getPlayers(): List<Player> = Groups.player.asList()

    suspend fun getPlayersAsync(): List<Player> = runMindustryThread { Groups.player.asList().toList() }

    fun findPlayerByID(id: Int): Player? = Groups.player.getByID(id)

    fun getUnits(): List<mindustry.gen.Unit> = Groups.unit.asList()
}
