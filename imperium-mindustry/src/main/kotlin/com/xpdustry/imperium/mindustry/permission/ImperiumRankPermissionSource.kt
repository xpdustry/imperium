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

import com.xpdustry.distributor.api.permission.MutablePermissionTree
import com.xpdustry.distributor.api.permission.PermissionTree
import com.xpdustry.distributor.api.permission.rank.EnumRankNode
import com.xpdustry.distributor.api.permission.rank.RankNode
import com.xpdustry.distributor.api.permission.rank.RankPermissionSource
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.config.ImperiumConfig

class ImperiumRankPermissionSource(private val config: ImperiumConfig) : RankPermissionSource {
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
