// SPDX-License-Identifier: GPL-3.0-only
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
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.account.PlayerLoginEvent
import com.xpdustry.imperium.mindustry.account.PlayerLogoutEvent
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.registerDistributorService
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.store.DataStoreService
import java.util.Collections
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Player

@Inject
class ImperiumPermissionListener(
    private val plugin: MindustryPlugin,
    private val config: ImperiumConfig,
    private val store: DataStoreService,
    private val users: UserManager,
) : ImperiumApplication.Listener {
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
            val account = store.selectAccountBySessionKey(player.sessionKey)
            val nodes = ArrayList<RankNode>()
            val rank = account?.account?.rank ?: Rank.EVERYONE
            nodes += EnumRankNode.linear(rank, "imperium", true)
            nodes += account?.achievements.orEmpty().map { EnumRankNode.singular(it, "imperium") }
            val undercover = users.getSetting(player.uuid(), User.Setting.UNDERCOVER)
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
