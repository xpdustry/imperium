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
import com.xpdustry.imperium.common.account.AchievementCompletedMessage
import com.xpdustry.imperium.common.account.RankChangeEvent
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.launch

class RoleSyncListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val accounts = instances.get<AccountManager>()
    private val messenger = instances.get<Messenger>()

    override fun onImperiumInit() {
        discord.getMainServer().addServerMemberJoinListener {
            ImperiumScope.MAIN.launch { discord.syncRoles(it.user) }
        }

        discord.getMainServer().addUserRoleAddListener {
            ImperiumScope.MAIN.launch { discord.syncRoles(it.user) }
        }

        discord.getMainServer().addUserRoleRemoveListener {
            ImperiumScope.MAIN.launch { discord.syncRoles(it.user) }
        }

        messenger.consumer<RankChangeEvent> { (snowflake) -> discord.syncRoles(snowflake) }

        messenger.consumer<AchievementCompletedMessage> { (snowflake, _) ->
            discord.syncRoles(snowflake)
        }
    }

    @Command(["sync-roles"])
    private suspend fun onSyncRolesCommand(sender: InteractionSender.Slash) {
        val account = accounts.findByDiscord(sender.user.id)
        if (account == null) {
            sender.respond("You are not linked to a cn account.")
            return
        }
        discord.syncRoles(account.snowflake)
        sender.respond(
            "Your discord roles have been synchronized with your account rank and achievements.")
    }
}
