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
package com.xpdustry.imperium.common.account.sql

import com.google.common.annotations.VisibleForTesting
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.hash.Argon2HashFunction
import com.xpdustry.imperium.common.hash.Argon2Params
import com.xpdustry.imperium.common.hash.GenericSaltyHashFunction
import com.xpdustry.imperium.common.hash.Hash
import com.xpdustry.imperium.common.hash.PBKDF2Params
import com.xpdustry.imperium.common.hash.ShaHashFunction
import com.xpdustry.imperium.common.hash.ShaType
import com.xpdustry.imperium.common.misc.exists
import com.xpdustry.imperium.common.security.DEFAULT_PASSWORD_REQUIREMENTS
import com.xpdustry.imperium.common.security.DEFAULT_USERNAME_REQUIREMENTS
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.PasswordRequirement
import com.xpdustry.imperium.common.security.UsernameRequirement
import com.xpdustry.imperium.common.security.findMissingPasswordRequirements
import com.xpdustry.imperium.common.security.findMissingUsernameRequirements
import com.xpdustry.imperium.common.snowflake.SnowflakeGenerator
import com.xpdustry.imperium.common.snowflake.timestamp
import java.time.Duration
import java.time.Instant
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

interface SQLAccountManager {
    suspend fun findByUsername(username: String): Account?

    suspend fun findByDiscord(discord: Long): Account?

    suspend fun findByIdentity(identity: Identity.Mindustry): Account?

    suspend fun existsByIdentity(identity: Identity.Mindustry): Boolean

    suspend fun register(
        username: String,
        password: CharArray,
        identity: Identity.Mindustry
    ): AccountResult

    suspend fun migrate(
        oldUsername: String,
        newUsername: String,
        password: CharArray
    ): AccountResult

    suspend fun changePassword(
        oldPassword: CharArray,
        newPassword: CharArray,
        identity: Identity.Mindustry
    ): AccountResult

    suspend fun login(
        username: String,
        password: CharArray,
        identity: Identity.Mindustry
    ): AccountResult

    suspend fun refresh(identity: Identity.Mindustry): AccountResult

    suspend fun logout(identity: Identity.Mindustry, all: Boolean = false): AccountResult
}

sealed interface AccountResult {

    data object Success : AccountResult

    data object AlreadyRegistered : AccountResult

    data object AccountNotFound : AccountResult

    data object NotLogged : AccountResult

    data object AlreadyLogged : AccountResult

    data object WrongPassword : AccountResult

    data class InvalidPassword(val missing: List<PasswordRequirement>) : AccountResult

    data class InvalidUsername(val missing: List<UsernameRequirement>) : AccountResult
}

class SimpleSQLAccountManager(
    private val database: Database,
    private val snowflake: SnowflakeGenerator
) : SQLAccountManager, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        transaction(database) {
            SchemaUtils.create(
                AccountTable,
                AccountSessionTable,
                AccountPermissionTable,
                AccountAchievementTable,
                LegacyAccountTable,
                LegacyAccountAchievementTable)
        }
    }

    override suspend fun findByUsername(username: String): Account? =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            AccountTable.select { AccountTable.username eq username }.firstOrNull()?.toAccount()
        }

    override suspend fun findByDiscord(discord: Long): Account? =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            AccountTable.select { AccountTable.discord eq discord }.firstOrNull()?.toAccount()
        }

    override suspend fun findByIdentity(identity: Identity.Mindustry): Account? =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            val sessionHash = createSessionHash(identity)
            (AccountTable leftJoin AccountSessionTable)
                .select {
                    (AccountSessionTable.hash eq sessionHash) and
                        (AccountSessionTable.expiration less Instant.now())
                }
                .firstOrNull()
                ?.toAccount()
        }

    override suspend fun existsByIdentity(identity: Identity.Mindustry): Boolean =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            val sessionHash = createSessionHash(identity)
            AccountSessionTable.exists {
                (AccountSessionTable.hash eq sessionHash) and
                    (AccountSessionTable.expiration less Instant.now())
            }
        }

    override suspend fun register(
        username: String,
        password: CharArray,
        identity: Identity.Mindustry
    ): AccountResult =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            if (AccountTable.exists { AccountTable.username eq username }) {
                return@newSuspendedTransaction AccountResult.AlreadyRegistered
            }

            val hashUsr = ShaHashFunction.create(username.toCharArray(), ShaType.SHA256).hash
            if (LegacyAccountTable.exists { LegacyAccountTable.usernameHash eq hashUsr }) {
                return@newSuspendedTransaction AccountResult.InvalidUsername(
                    listOf(UsernameRequirement.Reserved(username)))
            }

            val missingPwdRequirements =
                DEFAULT_PASSWORD_REQUIREMENTS.findMissingPasswordRequirements(password)
            if (missingPwdRequirements.isNotEmpty()) {
                return@newSuspendedTransaction AccountResult.InvalidPassword(missingPwdRequirements)
            }

            val missingUsrRequirements =
                DEFAULT_USERNAME_REQUIREMENTS.findMissingUsernameRequirements(username)
            if (missingUsrRequirements.isNotEmpty()) {
                return@newSuspendedTransaction AccountResult.InvalidUsername(missingUsrRequirements)
            }

            val hashPwd = GenericSaltyHashFunction.create(password, PASSWORD_PARAMS)
            AccountTable.insert {
                it[id] = snowflake.generate()
                it[AccountTable.username] = username
                it[passwordHash] = hashPwd.hash
                it[passwordSalt] = hashPwd.salt
            }

            return@newSuspendedTransaction AccountResult.Success
        }

    override suspend fun migrate(
        oldUsername: String,
        newUsername: String,
        password: CharArray
    ): AccountResult =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            val oldUsernameHash =
                ShaHashFunction.create(oldUsername.toCharArray(), ShaType.SHA256).hash

            val oldAccount =
                LegacyAccountTable.select { LegacyAccountTable.usernameHash eq oldUsernameHash }
                    .limit(1)
                    .firstOrNull()
                    ?: return@newSuspendedTransaction AccountResult.AccountNotFound

            if (!GenericSaltyHashFunction.equals(
                password,
                Hash(
                    oldAccount[LegacyAccountTable.passwordHash],
                    oldAccount[LegacyAccountTable.passwordSalt],
                    LEGACY_PASSWORD_PARAMS))) {
                return@newSuspendedTransaction AccountResult.WrongPassword
            }

            if (AccountTable.exists { AccountTable.username eq newUsername }) {
                return@newSuspendedTransaction AccountResult.AlreadyRegistered
            }

            val missing = DEFAULT_USERNAME_REQUIREMENTS.findMissingUsernameRequirements(newUsername)
            if (missing.isNotEmpty()) {
                return@newSuspendedTransaction AccountResult.InvalidUsername(missing)
            }

            val newPasswordHash = GenericSaltyHashFunction.create(password, PASSWORD_PARAMS)
            val newAccount =
                AccountTable.insertAndGetId {
                    it[username] = newUsername
                    it[passwordHash] = newPasswordHash.hash
                    it[passwordSalt] = newPasswordHash.salt
                    it[playtime] = oldAccount[LegacyAccountTable.playtime]
                    it[games] = oldAccount[LegacyAccountTable.games]
                    it[verified] = oldAccount[LegacyAccountTable.verified]
                }

            val oldAchievements =
                LegacyAccountAchievementTable.select {
                    LegacyAccountAchievementTable.account eq oldAccount[LegacyAccountTable.id]
                }

            AccountAchievementTable.batchInsert(oldAchievements) {
                this[AccountAchievementTable.account] = newAccount
                this[AccountAchievementTable.achievement] =
                    it[LegacyAccountAchievementTable.achievement]
                this[AccountAchievementTable.completed] = true
            }

            LegacyAccountTable.deleteWhere { id eq oldAccount[LegacyAccountTable.id] }

            return@newSuspendedTransaction AccountResult.Success
        }

    override suspend fun changePassword(
        oldPassword: CharArray,
        newPassword: CharArray,
        identity: Identity.Mindustry
    ): AccountResult =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            val sessionHash = createSessionHash(identity)
            val account =
                (AccountTable leftJoin AccountSessionTable)
                    .slice(AccountTable.id, AccountTable.passwordHash, AccountTable.passwordSalt)
                    .select { AccountSessionTable.hash eq sessionHash }
                    .firstOrNull()
                    ?: return@newSuspendedTransaction AccountResult.NotLogged

            if (!GenericSaltyHashFunction.equals(
                oldPassword,
                Hash(
                    account[AccountTable.passwordHash],
                    account[AccountTable.passwordSalt],
                    PASSWORD_PARAMS))) {
                return@newSuspendedTransaction AccountResult.WrongPassword
            }

            val missing = DEFAULT_PASSWORD_REQUIREMENTS.findMissingPasswordRequirements(newPassword)
            if (missing.isNotEmpty()) {
                return@newSuspendedTransaction AccountResult.InvalidPassword(missing)
            }

            val newPasswordHash = GenericSaltyHashFunction.create(newPassword, PASSWORD_PARAMS)
            AccountTable.update({ AccountTable.id eq account[AccountTable.id] }) {
                it[passwordHash] = newPasswordHash.hash
                it[passwordSalt] = newPasswordHash.salt
            }

            return@newSuspendedTransaction AccountResult.Success
        }

    override suspend fun login(
        username: String,
        password: CharArray,
        identity: Identity.Mindustry
    ): AccountResult =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            val result =
                AccountTable.slice(
                        AccountTable.id, AccountTable.passwordHash, AccountTable.passwordSalt)
                    .select { AccountTable.username eq username }
                    .firstOrNull()
                    ?: return@newSuspendedTransaction AccountResult.AccountNotFound

            if (!GenericSaltyHashFunction.equals(
                password,
                Hash(
                    result[AccountTable.passwordHash],
                    result[AccountTable.passwordSalt],
                    PASSWORD_PARAMS))) {
                return@newSuspendedTransaction AccountResult.AccountNotFound
            }

            val sessionHash = createSessionHash(identity)
            if (AccountSessionTable.exists { AccountSessionTable.hash eq sessionHash }) {
                return@newSuspendedTransaction AccountResult.AlreadyLogged
            }

            AccountSessionTable.insert {
                it[account] = result[AccountTable.id]
                it[hash] = sessionHash
                it[expiration] = Instant.now().plus(SESSION_TOKEN_DURATION)
            }

            return@newSuspendedTransaction AccountResult.Success
        }

    override suspend fun refresh(identity: Identity.Mindustry): AccountResult =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            val sessionHash = createSessionHash(identity)

            AccountSessionTable.deleteWhere {
                (hash eq sessionHash) and (expiration less Instant.now())
            }

            val updated =
                AccountSessionTable.update({ AccountSessionTable.hash eq sessionHash }) {
                    it[expiration] = Instant.now().plus(SESSION_TOKEN_DURATION)
                }

            if (updated == 0) {
                AccountResult.NotLogged
            } else {
                AccountResult.Success
            }
        }

    override suspend fun logout(identity: Identity.Mindustry, all: Boolean): AccountResult =
        newSuspendedTransaction(ImperiumScope.IO.coroutineContext, database) {
            val sessionHash = createSessionHash(identity)
            val session =
                AccountSessionTable.slice(AccountSessionTable.account)
                    .select { AccountSessionTable.hash eq sessionHash }
                    .firstOrNull()
                    ?: return@newSuspendedTransaction AccountResult.AccountNotFound

            if (all) {
                AccountSessionTable.deleteWhere { account eq session[account] }
            } else {
                AccountSessionTable.deleteWhere { hash eq sessionHash }
            }

            AccountResult.Success
        }

    @VisibleForTesting
    internal suspend fun createSessionHash(identity: Identity.Mindustry): ByteArray =
        Argon2HashFunction.create(
                identity.uuid.toCharArray(), SESSION_TOKEN_PARAMS, identity.usid.toCharArray())
            .hash

    private fun ResultRow.toAccount() =
        Account(
            snowflake = this[AccountTable.id].value,
            username = this[AccountTable.username],
            discord = this[AccountTable.discord],
            games = this[AccountTable.games],
            playtime = this[AccountTable.playtime],
            creation = this[AccountTable.id].value.timestamp,
            legacy = this[AccountTable.legacy],
            verified = this[AccountTable.verified])

    companion object {
        private val SESSION_TOKEN_DURATION = Duration.ofDays(7L)

        private val SESSION_TOKEN_PARAMS =
            Argon2Params(
                memory = 19,
                iterations = 2,
                length = 32,
                saltLength = 8,
                parallelism = 8,
                type = Argon2Params.Type.ID,
                version = Argon2Params.Version.V13,
            )

        private val PASSWORD_PARAMS =
            Argon2Params(
                memory = 64 * 1024,
                iterations = 3,
                parallelism = 2,
                length = 64,
                type = Argon2Params.Type.ID,
                version = Argon2Params.Version.V13,
                saltLength = 64,
            )

        private val LEGACY_PASSWORD_PARAMS =
            PBKDF2Params(
                hmac = PBKDF2Params.Hmac.SHA256,
                iterations = 10000,
                length = 256,
                saltLength = 16,
            )
    }
}
