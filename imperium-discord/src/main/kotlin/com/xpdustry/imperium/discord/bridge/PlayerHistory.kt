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
package com.xpdustry.imperium.discord.bridge

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.MindustryPlayerMessage
import com.xpdustry.imperium.common.collection.LimitedList
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.security.Identity
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.random.nextInt

interface PlayerHistory {
    fun getPlayerJoins(server: String): List<Entry>?

    fun getPlayerQuits(server: String): List<Entry>?

    fun getPlayerEntry(tid: Int): Entry?

    data class Entry(
        val player: Identity.Mindustry,
        val tid: Int,
        val timestamp: Instant = Instant.now()
    )
}

class SimplePlayerHistory(private val messenger: Messenger) :
    PlayerHistory, ImperiumApplication.Listener {

    private val joins = ConcurrentHashMap<String, LimitedList<PlayerHistory.Entry>>()
    private val quits = ConcurrentHashMap<String, LimitedList<PlayerHistory.Entry>>()

    override fun onImperiumInit() {
        messenger.consumer<MindustryPlayerMessage> {
            val map =
                when (it.action) {
                    MindustryPlayerMessage.Action.Join -> joins
                    MindustryPlayerMessage.Action.Quit -> quits
                    else -> return@consumer
                }
            val entry = PlayerHistory.Entry(it.player, Random.nextInt(100000..999999))
            map.computeIfAbsent(it.serverName) { LimitedList(30) }.add(entry)
        }
    }

    override fun getPlayerJoins(server: String): List<PlayerHistory.Entry>? = joins[server]

    override fun getPlayerQuits(server: String): List<PlayerHistory.Entry>? = quits[server]

    override fun getPlayerEntry(tid: Int): PlayerHistory.Entry? {
        for (list in listOf(joins, quits)) {
            for (entries in list.values) {
                for (entry in entries) {
                    if (entry.tid == tid) {
                        return entry
                    }
                }
            }
        }
        return null
    }
}
