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
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.session.MindustrySession
import com.xpdustry.imperium.common.session.MindustrySessionService
import jakarta.inject.Inject
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.serialization.Serializable

@Deprecated("no")
interface AccountManager {

    suspend fun selectByUsername(username: String): Account?

    suspend fun selectById(id: Int): Account?

    suspend fun selectByDiscord(discord: Long): Account?

    suspend fun updateDiscord(account: Int, discord: Long): Boolean

    suspend fun selectBySession(key: SessionKey): Account?

    suspend fun existsBySession(key: SessionKey): Boolean

    suspend fun existsById(id: Int): Boolean

    suspend fun incrementGames(account: Int): Boolean

    suspend fun incrementPlaytime(account: Int, duration: Duration): Boolean

    suspend fun updateRank(account: Int, rank: Rank)

    suspend fun updatePassword(account: Int, oldPassword: CharArray, newPassword: CharArray): AccountResult

    suspend fun selectAchievement(account: Int, achievement: Achievement): Boolean

    suspend fun selectAchievements(account: Int): Map<Achievement, Boolean>

    suspend fun updateAchievement(account: Int, achievement: Achievement, completed: Boolean): Boolean

    suspend fun selectMetadata(account: Int, key: String): String?

    suspend fun updateMetadata(account: Int, key: String, value: String)

    suspend fun register(username: String, password: CharArray): AccountResult

    suspend fun login(key: SessionKey, username: String, password: CharArray): AccountResult

    suspend fun logout(key: SessionKey, all: Boolean = false): Boolean
}

@Serializable data class AchievementCompletedMessage(val account: Int, val achievement: Achievement) : Message

class SimpleAccountManager
@Inject
constructor(
    private val accounts: AccountService,
    private val sessions: MindustrySessionService,
    private val achievements: AchievementService,
    private val metadata: MetadataService,
    private val messenger: Messenger,
) : AccountManager, LifecycleListener {
    override suspend fun selectByUsername(username: String): Account? = accounts.selectByUsername(username).getOrNull()

    override suspend fun selectById(id: Int): Account? = accounts.selectById(id).getOrNull()

    override suspend fun selectByDiscord(discord: Long): Account? = accounts.selectByDiscord(discord).getOrNull()

    override suspend fun updateDiscord(account: Int, discord: Long): Boolean = accounts.updateDiscord(account, discord)

    override suspend fun selectBySession(key: SessionKey): Account? =
        sessions.selectByKey(MindustrySession.Key(key.uuid, key.usid, key.address)).getOrNull()?.let {
            selectById(it.account)!!
        }

    override suspend fun existsBySession(key: SessionKey): Boolean =
        sessions.selectByKey(MindustrySession.Key(key.uuid, key.usid, key.address)).isPresent

    override suspend fun existsById(id: Int): Boolean = accounts.existsById(id)

    override suspend fun incrementGames(account: Int): Boolean = true

    override suspend fun incrementPlaytime(account: Int, duration: Duration): Boolean =
        accounts.incrementPlaytime(account, duration.toJavaDuration())

    override suspend fun updateRank(account: Int, rank: Rank) {
        if (accounts.updateRank(account, rank)) {
            messenger.publish(RankChangeEvent(account), local = true)
        }
    }

    override suspend fun updatePassword(account: Int, oldPassword: CharArray, newPassword: CharArray): AccountResult {
        return if (accounts.updatePassword(account, oldPassword, newPassword)) {
            AccountResult.Success
        } else {
            AccountResult.WrongPassword
        }
    }

    override suspend fun selectAchievement(account: Int, achievement: Achievement): Boolean =
        achievements.selectAchievementByAccount(account, achievement)

    override suspend fun selectAchievements(account: Int): Map<Achievement, Boolean> {
        val got = achievements.selectAllAchievements(account)
        return Achievement.entries.associateWith { it in got }
    }

    override suspend fun updateAchievement(account: Int, achievement: Achievement, completed: Boolean): Boolean {
        return achievements.upsertAchievement(account, achievement, completed)
    }

    override suspend fun selectMetadata(account: Int, key: String): String? {
        return metadata.selectMetadata(account, key)?.getOrNull()
    }

    override suspend fun updateMetadata(account: Int, key: String, value: String) {
        metadata.updateMetadata(account, key, value)
    }

    override suspend fun register(username: String, password: CharArray): AccountResult {
        accounts.register(username, password)
        return AccountResult.Success
    }

    override suspend fun login(key: SessionKey, username: String, password: CharArray): AccountResult {
        return if (sessions.login(MindustrySession.Key(key.uuid, key.usid, key.address), username, password)) {
            AccountResult.Success
        } else {
            AccountResult.NotFound
        }
    }

    override suspend fun logout(key: SessionKey, all: Boolean) =
        sessions.logout(MindustrySession.Key(key.uuid, key.usid, key.address), all)
}
