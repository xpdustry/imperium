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
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.security.permission.Permission
import com.xpdustry.imperium.common.security.permission.PermissionChangeMessage
import com.xpdustry.imperium.common.security.permission.PermissionManager
import com.xpdustry.imperium.common.security.permission.PermissionResult
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.service.DiscordService
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.future.await

class PermissionSyncListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val accounts = instances.get<AccountManager>()
    private val permissions = instances.get<PermissionManager>()
    private val messenger = instances.get<Messenger>()
    private val config = instances.get<ServerConfig.Discord>()

    override fun onImperiumInit() {
        messenger.consumer<PermissionChangeMessage> { (snowflake) ->
            val id = accounts.findBySnowflake(snowflake)?.discord ?: return@consumer
            val user = discord.api.getUserById(id).exceptionally { null }.await() ?: return@consumer
            val updater = discord.getMainServer().createUpdater()

            for ((permission, scope) in permissions.getPermissions(snowflake)) {
                for (element in config.permissions2roles[permission] ?: emptyList()) {
                    val role = discord.getMainServer().getRoleById(element).getOrNull() ?: continue
                    when (scope) {
                        is Permission.Scope.True -> updater.addRoleToUser(user, role)
                        else -> updater.removeRoleFromUser(user, role)
                    }
                }
            }

            updater.update().await()
        }
    }

    @Command(["sync-permissions"])
    private suspend fun onSyncRolesCommand(sender: InteractionSender.Slash) {
        val account = accounts.findByDiscord(sender.user.id)
        if (account == null) {
            sender.respond("You are not linked to a cn account.")
            return
        }

        val map = permissions.getPermissions(account.snowflake).toMutableMap()
        map.putIfAbsent(Permission.EVERYONE, Permission.Scope.True)
        map.putIfAbsent(Permission.VERIFIED, Permission.Scope.True)

        for (role in sender.user.getRoles(discord.getMainServer())) {
            for (permission in config.roles2permissions[role.id] ?: emptyList()) {
                map.putIfAbsent(permission, Permission.Scope.True)
            }
        }

        val result = permissions.setPermissions(account.snowflake, map)
        if (result != PermissionResult.SUCCESS) {
            sender.respond("Failed to synchronize your permission: **$result**")
            return
        }

        sender.respond("Your discord roles have been synchronized with your account.")
    }
}
