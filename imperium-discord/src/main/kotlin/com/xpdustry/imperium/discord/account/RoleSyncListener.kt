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
package com.xpdustry.imperium.discord.account

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.AchievementCompletedMessage
import com.xpdustry.imperium.common.account.RankChangeEvent
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.discord.misc.addSuspendingEventListener
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateBoostCountEvent
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

class RoleSyncListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val accounts = instances.get<AccountManager>()
    private val messenger = instances.get<Messenger>()

    override fun onImperiumInit() {
        discord.jda.addSuspendingEventListener<GuildMemberJoinEvent> { discord.syncRoles(it.member) }

        messenger.consumer<RankChangeEvent> { (id) -> discord.syncRoles(id) }

        messenger.consumer<AchievementCompletedMessage> { (id, _) -> discord.syncRoles(id) }

        runBlocking { syncServerBoosterRoles() }
        discord.jda.addSuspendingEventListener<GuildUpdateBoostCountEvent> { _ -> syncServerBoosterRoles() }
    }

    private suspend fun syncServerBoosterRoles() {
        discord.getMainServer().boosters.forEach { member ->
            val account = accounts.selectByDiscord(member.idLong) ?: return@forEach
            accounts.updateAchievement(account.id, Achievement.SUPPORTER, true)
        }
    }

    @ImperiumCommand(["sync-roles"])
    suspend fun onSyncRolesCommand(interaction: SlashCommandInteraction) {
        val reply = interaction.deferReply(true).await()
        val account = accounts.selectByDiscord(interaction.user.idLong)
        if (account == null) {
            reply.sendMessage("You are not linked to a cn account.").await()
            return
        }
        discord.syncRoles(account.id)
        reply.sendMessage("Your discord roles have been synchronized with your account rank and achievements.").await()
    }
}
