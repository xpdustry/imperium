// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.account.AccountAchievementService
import com.xpdustry.imperium.common.account.AccountService
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.discord.misc.await
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

@Inject
class AccountCommand
constructor(
    private val accounts: AccountService,
    private val achievements: AccountAchievementService,
    private val codec: IdentifierCodec,
) : ImperiumApplication.Listener {

    @ImperiumCommand(["account", "edit", "rank"], Rank.ADMIN)
    suspend fun onAccountRankSet(interaction: SlashCommandInteraction, target: String, rank: Rank) {
        val reply = interaction.deferReply(true).await()
        if (rank.ordinal >= accounts.selectByDiscord(interaction.user.idLong)!!.rank.ordinal) {
            reply.sendMessage("Nuh huh, you can only grant a rank lower than yours.").await()
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
        achievements.updateAchievement(id, achievement, completion)
        reply.sendMessage("Set ${achievement.name} achievement to $completion.").await()
    }
}
