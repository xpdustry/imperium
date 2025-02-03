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
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.discord.misc.await
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

class AccountCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val accounts = instances.get<AccountManager>()
    private val codec = instances.get<IdentifierCodec>()

    @ImperiumCommand(["account", "edit", "rank"], Rank.OWNER)
    suspend fun onAccountRankSet(interaction: SlashCommandInteraction, target: String, rank: Rank) {
        val reply = interaction.deferReply(true).await()
        if (rank == Rank.OWNER) {
            reply.sendMessage("Nuh huh").await()
            return
        }
        var id: Int? = null
        val parsed = codec.tryDecode(target)
        if (parsed != null && accounts.existsById(parsed)) {
            id = parsed
        } else if (target.toLongOrNull() != null) {
            id = accounts.selectByDiscord(target.toLong())?.id
        }
        if (id == null) {
            reply.sendMessage("Account not found.").await()
            return
        }
        accounts.updateRank(id, rank)
        reply.sendMessage("Set rank to $rank.").await()
    }

    @ImperiumCommand(["account", "edit", "achievement"], Rank.ADMIN)
    suspend fun onAccountAchievementSet(
        interaction: SlashCommandInteraction,
        target: String,
        achievement: Achievement,
        completion: Boolean,
    ) {
        val reply = interaction.deferReply(true).await()
        var id: Int? = null
        val parsed = codec.tryDecode(target)
        if (parsed != null && accounts.existsById(parsed)) {
            id = parsed
        } else if (target.toLongOrNull() != null) {
            id = accounts.selectByDiscord(target.toLong())?.id
        }
        if (id == null) {
            reply.sendMessage("Account not found.").await()
            return
        }
        accounts.updateAchievement(id, achievement, completion)
        reply.sendMessage("Set ${achievement.name} achievement to $completion.").await()
    }
}
