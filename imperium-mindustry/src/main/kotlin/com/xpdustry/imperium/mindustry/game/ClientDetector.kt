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
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import mindustry.Vars
import mindustry.gen.Player

interface ClientDetector {
    fun isFooClient(player: Player): Boolean
}

class SimpleClientDetector(plugin: MindustryPlugin) : ClientDetector, ImperiumApplication.Listener {

    private val fooClients = PlayerMap<Boolean>(plugin)

    override fun onImperiumInit() {
        Vars.netServer.addPacketHandler("fooCheck") { player, _ -> fooClients[player] = true }
    }

    override fun isFooClient(player: Player) = fooClients[player] == true
}
