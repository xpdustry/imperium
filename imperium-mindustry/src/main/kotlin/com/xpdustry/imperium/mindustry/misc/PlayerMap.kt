// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.misc

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.util.Priority
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
        Distributor.get().eventBus.subscribe(PlayerLeave::class.java, Priority.LOWEST, plugin) {
            players.remove(it.player.id())
        }
    }

    operator fun get(player: Player): V? = players[player.id()]

    operator fun set(player: Player, value: V): V? {
        val old = players[player.id()]
        players[player.id()] = value
        return old
    }

    fun clear() {
        players.clear()
    }

    fun remove(player: Player): V? = players.remove(player.id())
}
