// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.string.Password
import com.xpdustry.imperium.discord.misc.await
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction
import com.xpdustry.imperium.discord.command.MenuCommand
import com.xpdustry.imperium.discord.misc.MessageCreate
import net.dv8tion.jda.api.components.actionrow.ActionRow
import com.xpdustry.imperium.discord.service.DiscordService
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import java.security.SecureRandom
import kotlin.math.absoluteValue

class AccountCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val accounts = instances.get<AccountManager>()
    private val codec = instances.get<IdentifierCodec>()
    private val discord = instances.get<DiscordService>()
    private val config = instances.get<ImperiumConfig>()
    // maps a randId to an account id
    private val buttonMapping = mutableMapOf<Int,Int>()

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
        accounts.updateAchievement(id, achievement, completion)
        reply.sendMessage("Set ${achievement.name} achievement to $completion.").await()
    }

    @ImperiumCommand(["account", "recover", "user"], Rank.ADMIN)
    suspend fun onAccountRecover(interaction: SlashCommandInteraction, target: String, user: User? = null) {
        val reply = interaction.deferReply(true).await()
        var id: Int? = null
        val parsed = codec.tryDecode(target)
        if (parsed != null && accounts.existsById(parsed)) {
            id = parsed
        } else if (target.toLongOrNull() != null) {
            id = accounts.selectByDiscord(target.toLong())?.id
        }
        if (id == null) {
            reply.sendMessage("Account cannot be recovered, please contact Zetamap or Phinner for manual recovery.").await()
            return
        }
        val account = accounts.selectById(id) ?: reply.sendMessage("Account not found.").await()

        val channel = getSupportChannel()
        if (channel == null) {
            reply.sendMessage("Support channel not found, cannot send recovery request. Contact Server Owner").await()
            return
        }
        val targetUser = discord.jda.retrieveUserById(target).await()
        val secure = SecureRandom()
        val randId = secure.nextInt().absoluteValue // no negatives
        buttonMapping[randId] = id // can be duplicate, but low chance due to limited account recoveries
        channel.sendMessage(MessageCreate {
            // How do i translate this
            content = "${targetUser.asMention} Your account recovery has been requested by ${interaction.user.asMention}. Please confirm by pressing the button below."
            components += ActionRow.of(Button.primary("confirm_recovery:$randId:${targetUser.idLong}", "Confirm Recovery"))
        }).await()
        reply.sendMessage("Recovery request sent to the user.").await()
    }

    @MenuCommand("confirm_recovery")
    suspend fun onConfirmRecovery(interaction: ButtonInteraction) {
        interaction.deferReply(true).await()
        val parts = interaction.componentId.split(":")
        val accountId = parts[1].toInt()
        val account = buttonMapping[accountId]
        val targetDiscordId = parts[2].toLong()
        if (interaction.user.idLong != targetDiscordId) {
            val ignored = interaction.reply("This recovery confirmation is not for you.").setEphemeral(true).await()
            return
        }
        val code = generateRecoveryCode()
        val result = accounts.setPassword(accountId, Password(code))
        when (result) {
            AccountResult.Success -> {
                val ignored = interaction.reply("Your password has been set to `$code`. Please change it after logging in.").setEphemeral(true).await()
                val ignored2 = interaction.message.editMessageComponents().setComponents().await()
                Unit
            }
            is AccountResult.InvalidPassword -> {
                val ignored = interaction.reply("Invalid password: ${result.missing.joinToString()}").setEphemeral(true).await()
                Unit
            }
            else -> {
                val ignored = interaction.reply("Failed to recover account.").setEphemeral(true).await()
                Unit
            }
        }
    }

    private fun getSupportChannel(): TextChannel? {
        val channel = discord.getMainServer().getTextChannelById(config.discord.channels.support)
        if (channel == null) {
            LOGGER.error("Could not find support channel")
        }
        return channel
    }

    private fun generateRecoveryCode(): String {
        // remove ambiguous characters (0, O, 1, I, l)
        val charPool = "23456789AaBbCcDdEeFfGgHhJjKkLMmNnPpQqRrSsTtUuVvWwXxYyZz"
        val secureRandom = SecureRandom()
        val start: StringBuilder = StringBuilder()
        start.append("!1P") // ensure the code passes password req
        return start.append((1..6)
            .map { charPool[secureRandom.nextInt(charPool.length)] }
            .joinToString("")).toString()
    }

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}
