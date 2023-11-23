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
package com.xpdustry.imperium.discord.account

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.account.RankChangeEvent
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.service.DiscordService
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.future.await

class RankSyncListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val accounts = instances.get<AccountManager>()
    private val messenger = instances.get<Messenger>()
    private val config = instances.get<ServerConfig.Discord>()

    override fun onImperiumInit() {
        messenger.consumer<RankChangeEvent> { (snowflake) ->
            val account = accounts.findBySnowflake(snowflake) ?: return@consumer
            val userId = account.discord ?: return@consumer
            val user =
                discord.api.getUserById(userId).exceptionally { null }.await() ?: return@consumer
            val updater = discord.getMainServer().createUpdater()

            for (rank in account.rank.getRanksBelow()) {
                val roleId = config.ranks2roles[rank] ?: continue
                val role = discord.getMainServer().getRoleById(roleId).getOrNull() ?: continue
                updater.addRoleToUser(user, role)
                logger.debug(
                    "Added role {} (rank={}) to {} (id={})", role.name, rank, user.name, user.id)
            }

            updater.update().await()
        }
    }

    @Command(["sync-rank"])
    private suspend fun onSyncRolesCommand(sender: InteractionSender.Slash) {
        val account = accounts.findByDiscord(sender.user.id)
        if (account == null) {
            sender.respond("You are not linked to a cn account.")
            return
        }

        var rank = Rank.EVERYONE
        for (role in sender.user.getRoles(discord.getMainServer())) {
            rank = maxOf(Rank.EVERYONE, config.roles2ranks[role.id] ?: Rank.EVERYONE)
        }

        accounts.setRank(account.snowflake, rank)
        sender.respond("Your account rank have been synchronized with your discord roles.")
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
