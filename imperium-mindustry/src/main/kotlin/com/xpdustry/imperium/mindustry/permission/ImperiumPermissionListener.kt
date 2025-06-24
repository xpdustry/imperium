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
import com.xpdustry.distributor.api.permission.MutablePermissionTree
import com.xpdustry.distributor.api.permission.PermissionTree
import com.xpdustry.distributor.api.permission.rank.EnumRankNode
import com.xpdustry.distributor.api.permission.rank.RankNode
import com.xpdustry.distributor.api.permission.rank.RankPermissionSource
import com.xpdustry.distributor.api.permission.rank.RankProvider
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.common.user.Setting
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.account.PlayerLoginEvent
import com.xpdustry.imperium.mindustry.account.PlayerLogoutEvent
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.registerDistributorService
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import jakarta.inject.Inject
import java.util.Collections
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Player

class ImperiumPermissionListener
@Inject
constructor(
    private val plugin: MindustryPlugin,
    private val config: ImperiumConfig,
    private val accounts: AccountManager,
    private val users: UserManager,
) : LifecycleListener {
    private val ranks = PlayerMap<List<RankNode>>(plugin)

    override fun onImperiumInit() {
        registerDistributorService<RankPermissionSource>(plugin, ImperiumRankPermissionSource())
        registerDistributorService<RankProvider>(plugin, ImperiumRankProvider())
    }

    @EventHandler(priority = Priority.HIGH)
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        updatePlayerRanks(event.player)
    }

    @EventHandler(priority = Priority.HIGH)
    fun onPLayerLogin(event: PlayerLoginEvent) {
        updatePlayerRanks(event.player)
    }

    @EventHandler(priority = Priority.HIGH)
    fun onPlayerLogout(event: PlayerLogoutEvent) {
        updatePlayerRanks(event.player)
    }

    @TaskHandler(interval = 5L, unit = MindustryTimeUnit.SECONDS)
    fun refreshPlayerRank() {
        Entities.getPlayers().forEach(::updatePlayerRanks)
    }

    private fun updatePlayerRanks(player: Player) =
        ImperiumScope.MAIN.launch {
            val account = accounts.selectBySession(player.sessionKey)
            val achievements = account?.let { accounts.selectAchievements(it.id) }.orEmpty()
            val nodes = ArrayList<RankNode>()
            val rank = account?.rank ?: Rank.EVERYONE
            nodes += EnumRankNode.linear(rank, "imperium", true)
            nodes += achievements.filterValues { it }.map { EnumRankNode.singular(it.key, "imperium") }
            val undercover = users.getSetting(player.uuid(), Setting.UNDERCOVER)
            runMindustryThread {
                ranks[player] = Collections.unmodifiableList(nodes)
                player.admin = if (undercover) false else rank >= Rank.OVERSEER
            }
        }

    inner class ImperiumRankProvider : RankProvider {
        override fun getRanks(player: Player) = this@ImperiumPermissionListener.ranks[player].orEmpty()
    }

    inner class ImperiumRankPermissionSource : RankPermissionSource {
        override fun getRankPermissions(node: RankNode): PermissionTree {
            val tree = MutablePermissionTree.create()
            tree.setPermission("imperium.gamemode.${config.mindustry.gamemode.name.lowercase()}", true)
            if (node is EnumRankNode<*> && node.value is Rank) {
                (node.value as Rank).getRanksBelow().forEach { rank ->
                    tree.setPermission("imperium.rank.${rank.name.lowercase()}", true)
                }
            }
            if (node is EnumRankNode<*> && node.value is Achievement) {
                tree.setPermission("imperium.achievement.${(node.value as Achievement).name.lowercase()}", true)
            }
            return tree
        }
    }
}
