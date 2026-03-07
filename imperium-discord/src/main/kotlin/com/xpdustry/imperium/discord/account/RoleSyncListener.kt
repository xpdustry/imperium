// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.account

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.AchievementCompletedMessage
import com.xpdustry.imperium.common.account.RankChangeEvent
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
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
    private val messenger = instances.get<MessageService>()

    override fun onImperiumInit() {
        discord.jda.addSuspendingEventListener<GuildMemberJoinEvent> { discord.syncRoles(it.member) }

        messenger.subscribe<RankChangeEvent> { (id) -> discord.syncRoles(id) }

        messenger.subscribe<AchievementCompletedMessage> { (id, _) -> discord.syncRoles(id) }

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
