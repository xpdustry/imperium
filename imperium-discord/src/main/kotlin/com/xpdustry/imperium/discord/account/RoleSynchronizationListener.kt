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
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.launch

class RoleSynchronizationListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Discord>()
    private val api = instances.get<DiscordService>()
    private val accounts = instances.get<AccountManager>()

    override fun onImperiumInit() {
        api.getMainServer().addUserRoleAddListener {
            ImperiumScope.MAIN.launch {
                val account = accounts.findByDiscordId(it.user.id) ?: return@launch
                val added =
                    config.roles.entries.find { (_, id) -> id == it.role.id }?.key ?: return@launch
                accounts.updateById(account._id) { it.roles.add(added) }
            }
        }

        api.getMainServer().addUserRoleRemoveListener {
            ImperiumScope.MAIN.launch {
                val account = accounts.findByDiscordId(it.user.id) ?: return@launch
                val removed =
                    config.roles.entries.find { (_, id) -> id == it.role.id }?.key ?: return@launch
                accounts.updateById(account._id) { it.roles.remove(removed) }
            }
        }
    }

    @Command(["sync-roles"])
    private suspend fun onSyncRolesCommand(sender: InteractionSender.Slash) {
        val account = accounts.findByDiscordId(sender.user.id)
        if (account == null) {
            sender.respond("You are not linked to a cn account.")
            return
        }
        api.syncRoles(sender.user, account._id)
        sender.respond("Your discord roles have been synchronized with your account.")
    }
}
