// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.commands

import com.xpdustry.imperium.backend.misc.Embed
import com.xpdustry.imperium.backend.misc.await
import com.xpdustry.imperium.backend.service.DiscordService
import com.xpdustry.imperium.common.account.Account
import com.xpdustry.imperium.common.account.AccountAchievementService
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.account.AccountService
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.MindustrySessionService
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.string.Password
import com.xpdustry.imperium.common.string.toErrorMessage
import com.xpdustry.imperium.common.time.TimeRenderer
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

@Inject
class AccountCommand(
    private val accounts: AccountService,
    private val achievements: AccountAchievementService,
    private val sessions: MindustrySessionService,
    private val discord: DiscordService,
    private val renderer: TimeRenderer,
    private val codec: IdentifierCodec,
) : ImperiumApplication.Listener {

    @ImperiumCommand(["account", "info"], Rank.ADMIN)
    suspend fun onAccountInfo(interaction: SlashCommandInteraction, target: String) {
        val reply = interaction.deferReply(true).await()
        val account = findAccount(target) ?: return reply.sendAccountNotFound()
        reply
            .sendMessageEmbeds(
                Embed {
                    title = "Account Info"
                    field("ID", "`${codec.encode(account.id)}`")
                    field("Username", "`${account.username}`")
                    field("Rank", account.rank.name)
                    field("Discord", account.discord?.let { "<@$it>" } ?: "none")
                    field("Games", account.games.toString())
                    field("Playtime", renderer.renderDuration(account.playtime))
                    field("Creation", renderer.renderInstant(account.creation))
                    field("Legacy", account.legacy.toString())
                    field(
                        "Achievements",
                        achievements.selectAchievements(account.id).joinToString { it.name }.ifEmpty { "none" },
                    )
                }
            )
            .await()
    }

    @ImperiumCommand(["account", "create"], Rank.ADMIN)
    suspend fun onAccountCreate(
        interaction: SlashCommandInteraction,
        username: String,
        user: User? = null,
        password: String? = null,
    ) {
        val reply = interaction.deferReply(true).await()
        if (user != null && !reply.checkDiscordAvailable(user, null)) return

        val secret = password?.let(::Password) ?: Password.random()
        val result = accounts.register(username, secret)
        if (result != AccountResult.Success) {
            return reply.sendAccountResult(result)
        }

        val account = accounts.selectByUsername(username)!!
        if (user != null) link(account, user)
        reply.sendMessage("Created the account ${account.display()}${user.suffix()}.\n${secret.display()}").await()
    }

    @ImperiumCommand(["account", "edit", "rank"], Rank.ADMIN)
    suspend fun onAccountRankSet(interaction: SlashCommandInteraction, target: String, rank: Rank) {
        val reply = interaction.deferReply(true).await()
        val account = findAccount(target) ?: return reply.sendAccountNotFound()
        val caller = discord.getRank(interaction.user)
        if (rank >= caller || account.rank >= caller) {
            reply.sendMessage("Nuh huh, you can only manage ranks lower than yours.").await()
            return
        }
        accounts.updateRank(account.id, rank)
        reply.sendMessage("Set the rank of ${account.display()} to $rank.").await()
    }

    @ImperiumCommand(["account", "edit", "achievement"], Rank.ADMIN)
    suspend fun onAccountAchievementSet(
        interaction: SlashCommandInteraction,
        target: String,
        achievement: Achievement,
        completion: Boolean,
    ) {
        val reply = interaction.deferReply(true).await()
        val account = findAccount(target) ?: return reply.sendAccountNotFound()
        achievements.updateAchievement(account.id, achievement, completion)
        reply.sendMessage("Set the ${achievement.name} achievement of ${account.display()} to $completion.").await()
    }

    @ImperiumCommand(["account", "edit", "password"], Rank.ADMIN)
    suspend fun onAccountPasswordSet(interaction: SlashCommandInteraction, target: String, password: String? = null) {
        val reply = interaction.deferReply(true).await()
        val account = findAccount(target) ?: return reply.sendAccountNotFound()
        if (!reply.checkCanModify(interaction.user, account)) return

        val secret = password?.let(::Password) ?: Password.random()
        val result = accounts.updatePassword(account.id, secret)
        if (result != AccountResult.Success) {
            return reply.sendAccountResult(result)
        }

        // The password might have been changed because the account was compromised
        sessions.logoutAll(account.id)
        reply
            .sendMessage(
                "Changed the password of ${account.display()}, its sessions have been revoked.\n${secret.display()}"
            )
            .await()
    }

    @ImperiumCommand(["account", "edit", "username"], Rank.ADMIN)
    suspend fun onAccountUsernameSet(interaction: SlashCommandInteraction, target: String, username: String) {
        val reply = interaction.deferReply(true).await()
        val account = findAccount(target) ?: return reply.sendAccountNotFound()
        if (!reply.checkCanModify(interaction.user, account)) return

        val result = accounts.updateUsername(account.id, username)
        if (result != AccountResult.Success) {
            return reply.sendAccountResult(result)
        }
        reply.sendMessage("Renamed ${account.display()} to `$username`.").await()
    }

    @ImperiumCommand(["account", "edit", "discord"], Rank.ADMIN)
    suspend fun onAccountDiscordSet(interaction: SlashCommandInteraction, target: String, user: User? = null) {
        val reply = interaction.deferReply(true).await()
        val account = findAccount(target) ?: return reply.sendAccountNotFound()
        if (!reply.checkCanModify(interaction.user, account)) return

        if (user == null) {
            accounts.updateDiscord(account.id, null)
            reply.sendMessage("Unlinked ${account.display()} from its discord account.").await()
            return
        }

        if (!reply.checkDiscordAvailable(user, account.id)) return
        link(account, user)
        reply.sendMessage("Linked ${account.display()}${user.suffix()}.").await()
    }

    @ImperiumCommand(["account", "legacy", "info"], Rank.ADMIN)
    suspend fun onLegacyAccountInfo(interaction: SlashCommandInteraction, username: String) {
        val reply = interaction.deferReply(true).await()
        val legacy = accounts.selectLegacyByUsername(username) ?: return reply.sendLegacyAccountNotFound()
        reply
            .sendMessageEmbeds(
                Embed {
                    title = "Legacy Account Info"
                    field("Username", "`$username`")
                    field("Rank", legacy.rank.name)
                    field("Games", legacy.games.toString())
                    field("Playtime", renderer.renderDuration(legacy.playtime))
                    field("Achievements", legacy.achievements.joinToString { it.name }.ifEmpty { "none" })
                }
            )
            .await()
    }

    @ImperiumCommand(["account", "legacy", "migrate"], Rank.ADMIN)
    suspend fun onLegacyAccountMigrate(
        interaction: SlashCommandInteraction,
        legacy: String,
        username: String,
        user: User? = null,
        password: String? = null,
    ) {
        val reply = interaction.deferReply(true).await()
        val previous = accounts.selectLegacyByUsername(legacy) ?: return reply.sendLegacyAccountNotFound()
        if (previous.rank >= discord.getRank(interaction.user)) {
            reply.sendMessage("Nuh huh, you can only migrate legacy accounts with a rank lower than yours.").await()
            return
        }
        if (user != null && !reply.checkDiscordAvailable(user, null)) return

        val secret = password?.let(::Password) ?: Password.random()
        val result = accounts.migrateLegacy(legacy, username, secret)
        if (result != AccountResult.Success) {
            return reply.sendAccountResult(result)
        }

        val account = accounts.selectByUsername(username)!!
        if (user != null) link(account, user)
        reply
            .sendMessage(
                "Migrated the legacy account `$legacy` to ${account.display()}${user.suffix()}.\n${secret.display()}"
            )
            .await()
    }

    @ImperiumCommand(["account", "legacy", "delete"], Rank.ADMIN)
    suspend fun onLegacyAccountDelete(interaction: SlashCommandInteraction, username: String) {
        val reply = interaction.deferReply(true).await()
        val legacy = accounts.selectLegacyByUsername(username) ?: return reply.sendLegacyAccountNotFound()
        if (legacy.rank >= discord.getRank(interaction.user)) {
            reply.sendMessage("Nuh huh, you can only delete legacy accounts with a rank lower than yours.").await()
            return
        }
        accounts.deleteLegacy(username)
        reply.sendMessage("Deleted the legacy account `$username`, its username is now available.").await()
    }

    /** Accepts a discord mention or snowflake, an account username, or an encoded account identifier. */
    private suspend fun findAccount(target: String): Account? {
        val snowflake = DISCORD_USER_REGEX.matchEntire(target.trim())?.groupValues?.get(1)?.toLong()
        if (snowflake != null) {
            return accounts.selectByDiscord(snowflake)
        }
        return accounts.selectByUsername(target) ?: codec.tryDecode(target)?.let { accounts.selectById(it) }
    }

    private suspend fun link(account: Account, user: User) {
        accounts.updateDiscord(account.id, user.idLong)
        if (account.rank < Rank.VERIFIED) {
            accounts.updateRank(account.id, Rank.VERIFIED)
        }
        discord.syncRoles(account.id)
    }

    // Admins can modify their own account, or the accounts of lower ranked players
    private suspend fun InteractionHook.checkCanModify(user: User, account: Account): Boolean {
        if (account.discord == user.idLong || account.rank < discord.getRank(user)) {
            return true
        }
        sendMessage("Nuh huh, you can only modify accounts with a rank lower than yours.").await()
        return false
    }

    private suspend fun InteractionHook.checkDiscordAvailable(user: User, account: Int?): Boolean {
        val linked = accounts.selectByDiscord(user.idLong)
        if (linked == null || linked.id == account) {
            return true
        }
        sendMessage("${user.asMention} is already linked to ${linked.display()}, unlink it first.").await()
        return false
    }

    private suspend fun InteractionHook.sendAccountNotFound() {
        sendMessage("Account not found.").await()
    }

    private suspend fun InteractionHook.sendLegacyAccountNotFound() {
        sendMessage("Legacy account not found, keep in mind legacy usernames are case sensitive.").await()
    }

    private suspend fun InteractionHook.sendAccountResult(result: AccountResult) {
        val message =
            when (result) {
                is AccountResult.Success -> "Success."
                is AccountResult.AlreadyRegistered -> "This username is already taken."
                is AccountResult.NotFound -> "Account not found."
                is AccountResult.WrongPassword -> "Wrong password."
                is AccountResult.AlreadyLogged -> "Already logged in."
                is AccountResult.InvalidPassword ->
                    "The password does not meet the requirements:\n- ${result.missing.joinToString("\n- ") { it.toErrorMessage() }}"
                is AccountResult.InvalidUsername ->
                    "The username does not meet the requirements:\n- ${result.missing.joinToString("\n- ") { it.toErrorMessage() }}"
            }
        sendMessage(message).await()
    }

    private fun Account.display() = "`$username` (`${codec.encode(id)}`)"

    private fun Password.display() = "Password: ||`$value`||"

    private fun User?.suffix() = if (this == null) "" else " with the discord account $asMention"

    companion object {
        private val DISCORD_USER_REGEX = Regex("^(?:<@!?)?(\\d{17,20})>?$")
    }
}
