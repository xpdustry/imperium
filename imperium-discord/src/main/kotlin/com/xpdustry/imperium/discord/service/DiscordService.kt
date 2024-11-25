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
package com.xpdustry.imperium.discord.service

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.DiscordConfig
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.permission.Permission
import com.xpdustry.imperium.discord.misc.awaitVoid
import com.xpdustry.imperium.discord.misc.snowflake
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.FileProxy
import net.dv8tion.jda.api.utils.MemberCachePolicy
import okhttp3.OkHttpClient

interface DiscordService {
    val jda: JDA

    fun getMainServer(): Guild

    suspend fun isAllowed(user: User, rank: Rank): Boolean

    suspend fun isAllowed(user: User, permission: Permission): Boolean

    suspend fun syncRoles(id: Int)

    suspend fun syncRoles(member: Member)
}

class SimpleDiscordService(
    private val config: DiscordConfig,
    private val http: OkHttpClient,
    private val accounts: AccountManager,
) : DiscordService, ImperiumApplication.Listener {
    override lateinit var jda: JDA

    override fun onImperiumInit() {
        FileProxy.setDefaultHttpClient(http)
        jda =
            JDABuilder.create(
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.GUILD_MESSAGE_REACTIONS,
                    GatewayIntent.DIRECT_MESSAGES,
                    GatewayIntent.GUILD_EMOJIS_AND_STICKERS)
                .setToken(config.token.value)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .build()
                .awaitReady()
    }

    override fun getMainServer(): Guild = jda.guildCache.first()

    override suspend fun isAllowed(user: User, rank: Rank): Boolean {
        if (rank == Rank.EVERYONE) {
            return true
        }
        if ((accounts.selectByDiscord(user.idLong)?.rank ?: Rank.EVERYONE) >= rank) {
            return true
        }

        var max = Rank.EVERYONE
        for (role in (getMainServer().getMemberById(user.idLong)?.roles ?: emptyList())) {
            max = maxOf(max, config.roles2ranks[role.idLong] ?: Rank.EVERYONE)
        }
        return max >= rank
    }

    override suspend fun isAllowed(user: User, permission: Permission): Boolean =
        getMainServer().getMemberById(user.idLong)?.roles?.any {
            it.idLong == config.permissions2roles[permission]
        } ?: false

    override suspend fun syncRoles(member: Member) {
        val account = accounts.selectByDiscord(member.idLong) ?: return
        syncRoles(account.id)
    }

    override suspend fun syncRoles(id: Int) {
        val account = accounts.selectById(id)
        val discord = account?.discord ?: return
        val member = getMainServer().getMemberById(discord) ?: return
        val current = member.roles.associateBy(Role::getIdLong)

        for ((achievement, completed) in accounts.selectAchievements(id)) {
            val roleId = config.achievements2roles[achievement] ?: continue
            val role = getMainServer().getRoleById(roleId) ?: continue
            if (completed) {
                if (roleId in current.keys) continue
                getMainServer().addRoleToMember(member.snowflake, role).awaitVoid()
                logger.debug(
                    "Added achievement role {} (achievement={}) to {} (id={})",
                    role.name,
                    achievement,
                    member.effectiveName,
                    member.idLong)
            } else {
                if (roleId !in current.keys) continue
                getMainServer().removeRoleFromMember(member.snowflake, role).awaitVoid()
                logger.debug(
                    "Removed achievement role {} (achievement={}) from {} (id={})",
                    role.name,
                    achievement,
                    member.effectiveName,
                    member.idLong)
            }
        }

        val ranks = account.rank.getRanksBelow()
        for (rank in Rank.entries) {
            val roleId = config.ranks2roles[rank] ?: continue
            val role = getMainServer().getRoleById(roleId) ?: continue
            if (rank in ranks) {
                if (roleId in current) continue
                getMainServer().addRoleToMember(member.snowflake, role).awaitVoid()
                logger.debug(
                    "Added rank role {} (rank={}) to {} (id={})",
                    role.name,
                    rank,
                    member.effectiveName,
                    member.idLong)
            } else {
                if (roleId !in current) continue
                getMainServer().removeRoleFromMember(member.snowflake, role).awaitVoid()
                logger.debug(
                    "Removed rank role {} (rank={}) from {} (id={})",
                    role.name,
                    rank,
                    member.effectiveName,
                    member.idLong)
            }
        }
    }

    override fun onImperiumExit() {
        jda.shutdown()
        if (!jda.awaitShutdown(15.seconds.toJavaDuration())) {
            jda.shutdownNow()
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
