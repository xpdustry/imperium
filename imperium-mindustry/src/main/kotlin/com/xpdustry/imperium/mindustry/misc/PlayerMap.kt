/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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

import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import fr.xpdustry.distributor.api.util.Priority
import mindustry.game.EventType.PlayerLeave
import mindustry.gen.Player

class PlayerMap<V>(plugin: MindustryPlugin) {
    private val players = mutableMapOf<Int, V>()
    @Suppress("UNCHECKED_CAST")
    val entries: Sequence<Pair<Player, V>>
        get() =
            players.entries
                .asSequence()
                .map { entry -> Entities.findPlayerByID(entry.key) to entry.value }
                .filter { it.first != null } as Sequence<Pair<Player, V>>

    init {
        DistributorProvider.get().eventBus.subscribe(
            PlayerLeave::class.java, Priority.LOWEST, plugin) {
                players.remove(it.player.id())
            }
    }

    operator fun get(player: Player): V? = players[player.id()]

    operator fun set(player: Player, value: V) {
        players[player.id()] = value
    }

    fun remove(player: Player) {
        players.remove(player.id())
    }
}
