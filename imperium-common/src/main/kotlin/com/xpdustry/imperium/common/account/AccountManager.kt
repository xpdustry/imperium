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

import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.exists
import com.xpdustry.imperium.common.password.ImperiumHashFunctionV1
import com.xpdustry.imperium.common.password.PasswordHash
import com.xpdustry.imperium.common.password.PasswordHashFunction
import com.xpdustry.imperium.common.security.DEFAULT_PASSWORD_REQUIREMENTS
import com.xpdustry.imperium.common.security.DEFAULT_USERNAME_REQUIREMENTS
import com.xpdustry.imperium.common.security.UsernameRequirement
import com.xpdustry.imperium.common.security.findMissingPasswordRequirements
import com.xpdustry.imperium.common.security.findMissingUsernameRequirements
import jakarta.inject.Inject
import java.security.MessageDigest
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

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
constructor(private val provider: SQLProvider, private val messenger: Messenger, private val config: ImperiumConfig) :
    AccountManager, LifecycleListener {

    override fun onImperiumInit() {
        provider.newTransaction {
            SchemaUtils.create(
                AccountTable,
                AccountSessionTable,
                AccountAchievementTable,
                AccountMetadataTable,
                LegacyAccountTable,
                LegacyAccountAchievementTable,
            )

            if (config.testing) {
                LOGGER.warn("Testing mode enabled, creating test account, with credentials {}", "test:test")
                val password = runBlocking { PASSWORD_FUNCTION.hash("test".toCharArray()) }
                AccountTable.upsert {
                    it[username] = "test"
                    it[passwordHash] = password.hash
                    it[passwordSalt] = password.salt
                    it[rank] = Rank.OWNER
                }
            }

            AccountSessionTable.deleteWhere { expiration less Instant.now() }
        }
    }

    override suspend fun selectByUsername(username: String): Account? =
        provider.newSuspendTransaction {
            AccountTable.selectAll().where { AccountTable.username eq username }.firstOrNull()?.toAccount()
        }

    override suspend fun selectById(id: Int): Account? =
        provider.newSuspendTransaction {
            AccountTable.selectAll().where { AccountTable.id eq id }.firstOrNull()?.toAccount()
        }

    override suspend fun selectByDiscord(discord: Long): Account? =
        provider.newSuspendTransaction {
            AccountTable.selectAll().where { AccountTable.discord eq discord }.firstOrNull()?.toAccount()
        }

    override suspend fun updateDiscord(account: Int, discord: Long): Boolean =
        provider.newSuspendTransaction {
            AccountTable.update({ AccountTable.id eq account }) { it[AccountTable.discord] = discord } > 0
        }

    override suspend fun selectBySession(key: SessionKey): Account? =
        provider.newSuspendTransaction { selectBySession0(key)?.toAccount() }

    override suspend fun existsBySession(key: SessionKey): Boolean =
        provider.newSuspendTransaction { selectBySession0(key) != null }

    override suspend fun existsById(id: Int): Boolean =
        provider.newSuspendTransaction { AccountTable.exists { AccountTable.id eq id } }

    override suspend fun incrementGames(account: Int): Boolean =
        provider.newSuspendTransaction {
            AccountTable.update({ AccountTable.id eq account }) { it[games] = games.plus(1) } != 0
        }

    override suspend fun incrementPlaytime(account: Int, duration: Duration): Boolean =
        provider.newSuspendTransaction {
            AccountTable.update({ AccountTable.id eq account }) {
                it[playtime] = playtime.plus(duration.toJavaDuration())
            } != 0
        }

    override suspend fun updateRank(account: Int, rank: Rank) {
        val changed =
            provider.newSuspendTransaction {
                val current =
                    AccountTable.select(AccountTable.rank)
                        .where { AccountTable.id eq account }
                        .firstOrNull()
                        ?.get(AccountTable.rank)
                if (current == rank) {
                    return@newSuspendTransaction false
                }
                AccountTable.update({ AccountTable.id eq account }) { it[AccountTable.rank] = rank } != 0
            }
        if (changed) {
            messenger.publish(RankChangeEvent(account), local = true)
        }
    }

    override suspend fun updatePassword(account: Int, oldPassword: CharArray, newPassword: CharArray): AccountResult =
        provider.newSuspendTransaction {
            val result =
                AccountTable.select(AccountTable.passwordHash, AccountTable.passwordSalt)
                    .where { AccountTable.id eq account }
                    .firstOrNull() ?: return@newSuspendTransaction AccountResult.NotFound

            if (
                PASSWORD_FUNCTION.hash(oldPassword, result[AccountTable.passwordSalt]) !=
                    PasswordHash(result[AccountTable.passwordHash], result[AccountTable.passwordSalt])
            ) {
                return@newSuspendTransaction AccountResult.WrongPassword
            }

            val missing = DEFAULT_PASSWORD_REQUIREMENTS.findMissingPasswordRequirements(newPassword)
            if (missing.isNotEmpty()) {
                return@newSuspendTransaction AccountResult.InvalidPassword(missing)
            }

            val newPasswordHash = PASSWORD_FUNCTION.hash(newPassword)
            AccountTable.update({ AccountTable.id eq account }) {
                it[passwordHash] = newPasswordHash.hash
                it[passwordSalt] = newPasswordHash.salt
            }

            return@newSuspendTransaction AccountResult.Success
        }

    override suspend fun selectAchievement(account: Int, achievement: Achievement): Boolean =
        provider.newSuspendTransaction {
            AccountAchievementTable.select(AccountAchievementTable.completed)
                .where {
                    (AccountAchievementTable.account eq account) and
                        (AccountAchievementTable.achievement eq achievement)
                }
                .firstOrNull()
                ?.get(AccountAchievementTable.completed) ?: false
        }

    override suspend fun selectAchievements(account: Int): Map<Achievement, Boolean> =
        provider.newSuspendTransaction {
            AccountAchievementTable.select(AccountAchievementTable.achievement, AccountAchievementTable.completed)
                .where { AccountAchievementTable.account eq account }
                .associate { it[AccountAchievementTable.achievement] to it[AccountAchievementTable.completed] }
        }

    override suspend fun updateAchievement(account: Int, achievement: Achievement, completed: Boolean): Boolean {
        if (!existsById(account)) {
            return false
        }

        val wasCompleted =
            provider.newSuspendTransaction {
                AccountAchievementTable.select(AccountAchievementTable.completed)
                    .where {
                        (AccountAchievementTable.account eq account) and
                            (AccountAchievementTable.achievement eq achievement)
                    }
                    .firstOrNull()
                    ?.get(AccountAchievementTable.completed) ?: false
            }

        provider.newSuspendTransaction {
            AccountAchievementTable.upsert {
                it[AccountAchievementTable.account] = account
                it[AccountAchievementTable.achievement] = achievement
                it[AccountAchievementTable.completed] = completed
            }
        }

        if (!wasCompleted && completed) {
            messenger.publish(AchievementCompletedMessage(account, achievement), local = true)
        }

        return true
    }

    override suspend fun selectMetadata(account: Int, key: String): String? {
        return provider.newSuspendTransaction {
            AccountMetadataTable.select(AccountMetadataTable.value)
                .where { (AccountMetadataTable.account eq account) and (AccountMetadataTable.key eq key) }
                .firstOrNull()
                ?.get(AccountMetadataTable.value)
        }
    }

    override suspend fun updateMetadata(account: Int, key: String, value: String) {
        provider.newSuspendTransaction {
            AccountMetadataTable.upsert {
                it[AccountMetadataTable.account] = account
                it[AccountMetadataTable.key] = key
                it[AccountMetadataTable.value] = value
            }
        }
    }

    override suspend fun register(username: String, password: CharArray): AccountResult =
        provider.newSuspendTransaction {
            if (AccountTable.exists { AccountTable.username eq username }) {
                return@newSuspendTransaction AccountResult.AlreadyRegistered
            }

            val hashUsr = MessageDigest.getInstance("SHA-256").digest(username.toByteArray())
            if (LegacyAccountTable.exists { LegacyAccountTable.usernameHash eq hashUsr }) {
                return@newSuspendTransaction AccountResult.InvalidUsername(
                    listOf(UsernameRequirement.Reserved(username))
                )
            }

            val missingPwdRequirements = DEFAULT_PASSWORD_REQUIREMENTS.findMissingPasswordRequirements(password)
            if (missingPwdRequirements.isNotEmpty()) {
                return@newSuspendTransaction AccountResult.InvalidPassword(missingPwdRequirements)
            }

            val missingUsrRequirements = DEFAULT_USERNAME_REQUIREMENTS.findMissingUsernameRequirements(username)
            if (missingUsrRequirements.isNotEmpty()) {
                return@newSuspendTransaction AccountResult.InvalidUsername(missingUsrRequirements)
            }

            val hashPwd = PASSWORD_FUNCTION.hash(password)
            AccountTable.insert {
                it[AccountTable.username] = username
                it[passwordHash] = hashPwd.hash
                it[passwordSalt] = hashPwd.salt
            }

            return@newSuspendTransaction AccountResult.Success
        }

    override suspend fun login(key: SessionKey, username: String, password: CharArray): AccountResult =
        provider.newSuspendTransaction {
            if (
                AccountSessionTable.exists {
                    (AccountSessionTable.uuid eq key.uuid) and
                        (AccountSessionTable.usid eq key.usid) and
                        (AccountSessionTable.address eq key.address.address) and
                        (AccountSessionTable.expiration greaterEq Instant.now())
                }
            ) {
                return@newSuspendTransaction AccountResult.AlreadyLogged
            }

            val result =
                AccountTable.select(AccountTable.id, AccountTable.passwordHash, AccountTable.passwordSalt)
                    .where { AccountTable.username eq username }
                    .firstOrNull() ?: return@newSuspendTransaction AccountResult.NotFound

            if (
                PASSWORD_FUNCTION.hash(password, result[AccountTable.passwordSalt]) !=
                    PasswordHash(result[AccountTable.passwordHash], result[AccountTable.passwordSalt])
            ) {
                return@newSuspendTransaction AccountResult.NotFound
            }

            AccountSessionTable.insert {
                it[account] = result[AccountTable.id]
                it[uuid] = key.uuid
                it[usid] = key.usid
                it[address] = key.address.address
                it[expiration] = Instant.now().plus(SESSION_LIFETIME.toJavaDuration())
            }

            return@newSuspendTransaction AccountResult.Success
        }

    override suspend fun logout(key: SessionKey, all: Boolean) =
        provider.newSuspendTransaction {
            if (!all) {
                AccountSessionTable.deleteWhere {
                    (uuid eq key.uuid) and (usid eq key.usid) and (address eq key.address.address)
                } > 0
            } else {
                val sessions =
                    AccountSessionTable.select(AccountSessionTable.account)
                        .where { (AccountSessionTable.uuid eq key.uuid) }
                        .map { it[AccountSessionTable.account].value }
                return@newSuspendTransaction AccountSessionTable.deleteWhere { (account inList sessions) } > 0
            }
        }

    private fun selectBySession0(key: SessionKey): ResultRow? =
        (AccountTable leftJoin AccountSessionTable)
            .selectAll()
            .where {
                (AccountSessionTable.uuid eq key.uuid) and
                    (AccountSessionTable.usid eq key.usid) and
                    (AccountSessionTable.address eq key.address.address) and
                    (AccountSessionTable.expiration greaterEq Instant.now())
            }
            .firstOrNull()

    private fun ResultRow.toAccount() =
        Account(
            id = this[AccountTable.id].value,
            username = this[AccountTable.username],
            discord = this[AccountTable.discord],
            games = this[AccountTable.games],
            playtime = this[AccountTable.playtime].toKotlinDuration(),
            creation = this[AccountTable.creation],
            legacy = this[AccountTable.legacy],
            rank = this[AccountTable.rank],
        )

    companion object {
        private val LOGGER by LoggerDelegate()

        private val SESSION_LIFETIME = 30.days

        private val PASSWORD_FUNCTION: PasswordHashFunction = ImperiumHashFunctionV1()
    }
}
