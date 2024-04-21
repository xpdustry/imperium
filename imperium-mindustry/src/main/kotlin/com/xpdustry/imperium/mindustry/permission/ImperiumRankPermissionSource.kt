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

import com.xpdustry.distributor.permission.PermissionTree
import com.xpdustry.distributor.permission.TriState
import com.xpdustry.distributor.permission.rank.EnumRankNode
import com.xpdustry.distributor.permission.rank.RankNode
import com.xpdustry.distributor.permission.rank.RankPermissionSource
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.config.ServerConfig

class ImperiumRankPermissionSource(private val config: ServerConfig.Mindustry) :
    RankPermissionSource {
    override fun getRankPermissions(node: RankNode): PermissionTree {
        val tree = PermissionTree.create()
        tree.setPermission("imperium.gamemode.${config.gamemode.name.lowercase()}", TriState.TRUE)
        if (node is EnumRankNode<*> && node.value is Rank) {
            for (rank in (node.value as Rank).getRanksBelow()) {
                tree.setPermission("imperium.rank.${rank.name.lowercase()}", TriState.TRUE)
            }
        }
        return tree
    }
}
