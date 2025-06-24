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
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import mindustry.gen.Player

interface MarkedPlayerManager {
    fun isMarked(player: Player): Boolean

    fun mark(player: Player)
}

class SimpleMarkedPlayerManager(plugin: MindustryPlugin) : MarkedPlayerManager, ImperiumApplication.Listener {
    private val marked = PlayerMap<Boolean>(plugin)

    override fun isMarked(player: Player): Boolean = marked[player] ?: false

    override fun mark(player: Player) {
        marked[player] = true
    }
}
