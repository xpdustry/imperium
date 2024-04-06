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
package com.xpdustry.imperium.mindustry.permission

import com.xpdustry.distributor.annotation.method.EventHandler
import com.xpdustry.distributor.annotation.method.TaskHandler
import com.xpdustry.distributor.permission.rank.EnumRankNode
import com.xpdustry.distributor.permission.rank.RankNode
import com.xpdustry.distributor.permission.rank.RankProvider
import com.xpdustry.distributor.player.MUUID
import com.xpdustry.distributor.scheduler.MindustryTimeUnit
import com.xpdustry.distributor.util.Priority
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.misc.buildCache
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.identity
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Player

class ImperiumRankProvider(private val accounts: AccountManager) :
    RankProvider, ImperiumApplication.Listener {

    private val cache = buildCache<MUUID, Rank> { expireAfterWrite(20.seconds.toJavaDuration()) }

    override fun onImperiumInit() {}

    @EventHandler(priority = Priority.HIGH)
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        ImperiumScope.MAIN.launch { fetchPlayerRank(event.player) }

    @TaskHandler(interval = 10L, unit = MindustryTimeUnit.SECONDS)
    fun refreshPlayerRank() =
        ImperiumScope.MAIN.launch { Entities.getPlayersAsync().forEach { fetchPlayerRank(it) } }

    override fun getRanks(player: Player): Collection<RankNode> {
        val rank = cache.getIfPresent(MUUID.of(player)) ?: Rank.EVERYONE
        return listOf(EnumRankNode.linear(rank, { "imperium:" + it.name.lowercase() }, true))
    }

    private suspend fun fetchPlayerRank(player: Player) {
        cache.put(MUUID.of(player), accounts.findByIdentity(player.identity)?.rank ?: Rank.EVERYONE)
    }
}
