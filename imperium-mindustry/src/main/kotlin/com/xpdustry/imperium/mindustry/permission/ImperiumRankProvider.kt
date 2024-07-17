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
package com.xpdustry.imperium.mindustry.permission

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.permission.rank.EnumRankNode
import com.xpdustry.distributor.api.permission.rank.RankNode
import com.xpdustry.distributor.api.permission.rank.RankProvider
import com.xpdustry.distributor.api.player.MUUID
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.account.Account
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

    private val rank = buildCache<MUUID, Rank> { expireAfterWrite(20.seconds.toJavaDuration()) }
    private val achievements =
        buildCache<MUUID, Set<Account.Achievement>> {
            expireAfterWrite(20.seconds.toJavaDuration())
        }

    @EventHandler(priority = Priority.HIGH)
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        ImperiumScope.MAIN.launch { fetchPlayerInfo(event.player) }

    @TaskHandler(interval = 10L, unit = MindustryTimeUnit.SECONDS)
    fun refreshPlayerRank() =
        ImperiumScope.MAIN.launch { Entities.getPlayersAsync().forEach { fetchPlayerInfo(it) } }

    override fun getRanks(player: Player): List<RankNode> {
        val ranks =
            mutableListOf(
                EnumRankNode.linear(
                    rank.getIfPresent(MUUID.from(player)) ?: Rank.EVERYONE, "imperium", true))
        ranks.addAll(
            achievements.getIfPresent(MUUID.from(player)).orEmpty().map {
                EnumRankNode.singular(it, "imperium")
            })
        return ranks
    }

    private suspend fun fetchPlayerInfo(player: Player) {
        val account = accounts.findByIdentity(player.identity)
        rank.put(MUUID.from(player), account?.rank ?: Rank.EVERYONE)
        achievements.put(
            MUUID.from(player),
            (account?.let { accounts.getAchievements(it.snowflake) } ?: emptyMap())
                .filterValues { it.completed }
                .keys)
    }
}
