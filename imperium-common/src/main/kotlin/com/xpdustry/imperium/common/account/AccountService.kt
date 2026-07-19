// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.exists
import com.xpdustry.imperium.common.string.DEFAULT_PASSWORD_REQUIREMENTS
import com.xpdustry.imperium.common.string.DEFAULT_USERNAME_REQUIREMENTS
import com.xpdustry.imperium.common.string.HashedPassword
import com.xpdustry.imperium.common.string.ImperiumArgon2
import com.xpdustry.imperium.common.string.Password
import com.xpdustry.imperium.common.string.StringRequirement
import com.xpdustry.imperium.common.string.findMissingRequirements
import java.security.MessageDigest
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

@Inject
class AccountService(
    private val provider: SQLProvider,
    private val messenger: MessageService,
    private val config: ImperiumConfig,
) : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction {
            SchemaUtils.createMissingTablesAndColumns(AccountTable, LegacyAccountTable, LegacyAccountAchievementTable)

            if (config.testing) {
                LOGGER.warn("Testing mode enabled, creating test account, with credentials {}", "test:test")
                val hash = runBlocking { ImperiumArgon2.create(Password("test")) }
                AccountTable.upsert {
                    it[username] = "test"
                    it[rank] = Rank.OWNER
                    it[passwordHash] = hash.hash
                    it[passwordSalt] = hash.salt
                }
            }
        }
    }

    suspend fun register(username: String, password: Password): AccountResult {
        val missingPasswordRequirements = passwordRequirements().findMissingRequirements(password.value)
        if (missingPasswordRequirements.isNotEmpty()) {
            return AccountResult.InvalidPassword(missingPasswordRequirements)
        }

        val missingUsernameRequirements = usernameRequirements().findMissingRequirements(username)
        if (missingUsernameRequirements.isNotEmpty()) {
            return AccountResult.InvalidUsername(missingUsernameRequirements)
        }

        if (existsLegacyByUsername(username)) {
            return AccountResult.InvalidUsername(listOf(usernameRequirements().reservedRequirement()))
        }

        val hash = ImperiumArgon2.create(password)
        val identifier =
            provider.newSuspendTransaction {
                if (AccountTable.exists { AccountTable.username eq username }) {
                    return@newSuspendTransaction null
                }

                AccountTable.insert {
                        it[AccountTable.username] = username
                        it[passwordHash] = hash.hash
                        it[passwordSalt] = hash.salt
                    }[AccountTable.id]
                    .value
            }

        if (identifier == null) {
            return AccountResult.AlreadyRegistered
        }

        messenger.broadcast(AccountUpdate(identifier))
        return AccountResult.Success
    }

    suspend fun updatePassword(account: Int, oldPassword: Password, newPassword: Password): AccountResult {
        val current = selectPasswordById(account) ?: return AccountResult.NotFound
        if (!ImperiumArgon2.equals(oldPassword, current.hash, current.salt)) {
            return AccountResult.WrongPassword
        }

        val missing = passwordRequirements().findMissingRequirements(newPassword.value)
        if (missing.isNotEmpty()) {
            return AccountResult.InvalidPassword(missing)
        }

        val hash = ImperiumArgon2.create(newPassword)
        val updated =
            provider.newSuspendTransaction {
                AccountTable.update({ AccountTable.id eq account }) {
                    it[passwordHash] = hash.hash
                    it[passwordSalt] = hash.salt
                } > 0
            }
        if (!updated) {
            return AccountResult.NotFound
        }

        messenger.broadcast(AccountUpdate(account))
        return AccountResult.Success
    }

    suspend fun selectById(id: Int): Account? =
        provider.newSuspendTransaction {
            AccountTable.selectAll().where { AccountTable.id eq id }.firstOrNull()?.toAccount()
        }

    suspend fun existsById(id: Int): Boolean =
        provider.newSuspendTransaction { AccountTable.exists { AccountTable.id eq id } }

    suspend fun selectByUsername(username: String): Account? =
        provider.newSuspendTransaction {
            AccountTable.selectAll().where { AccountTable.username eq username }.firstOrNull()?.toAccount()
        }

    suspend fun existsByUsername(username: String): Boolean =
        provider.newSuspendTransaction { AccountTable.exists { AccountTable.username eq username } }

    suspend fun selectByDiscord(discord: Long): Account? =
        provider.newSuspendTransaction {
            AccountTable.selectAll().where { AccountTable.discord eq discord }.firstOrNull()?.toAccount()
        }

    suspend fun updateDiscord(account: Int, discord: Long): Boolean {
        val updated =
            provider.newSuspendTransaction {
                AccountTable.update({ AccountTable.id eq account }) { it[AccountTable.discord] = discord } > 0
            }
        if (updated) {
            messenger.broadcast(AccountUpdate(account))
        }
        return updated
    }

    suspend fun incrementGames(account: Int): Boolean {
        val updated =
            provider.newSuspendTransaction {
                AccountTable.update({ AccountTable.id eq account }) { it[games] = games.plus(1) } > 0
            }
        if (updated) {
            messenger.broadcast(AccountUpdate(account))
        }
        return updated
    }

    suspend fun incrementPlaytime(account: Int, duration: Duration): Boolean {
        val updated =
            provider.newSuspendTransaction {
                AccountTable.update({ AccountTable.id eq account }) { it[playtime] = playtime.plus(duration) } > 0
            }
        if (updated) {
            messenger.broadcast(AccountUpdate(account))
        }
        return updated
    }

    suspend fun updateRank(account: Int, rank: Rank): Boolean {
        val changed =
            provider.newSuspendTransaction {
                val current =
                    AccountTable.select(AccountTable.rank)
                        .where { AccountTable.id eq account }
                        .firstOrNull()
                        ?.get(AccountTable.rank) ?: return@newSuspendTransaction false
                if (current == rank) {
                    return@newSuspendTransaction false
                }

                AccountTable.update({ AccountTable.id eq account }) { it[AccountTable.rank] = rank } > 0
            }

        if (changed) {
            messenger.broadcast(AccountUpdate(account))
            messenger.broadcast(RankChangeEvent(account))
        }
        return changed
    }

    suspend fun selectPasswordById(id: Int): HashedPassword? =
        provider.newSuspendTransaction {
            AccountTable.select(AccountTable.passwordHash, AccountTable.passwordSalt)
                .where { AccountTable.id eq id }
                .firstOrNull()
                ?.let { HashedPassword(it[AccountTable.passwordHash], it[AccountTable.passwordSalt]) }
        }

    fun usernameRequirements(): List<StringRequirement> = DEFAULT_USERNAME_REQUIREMENTS

    fun passwordRequirements(): List<StringRequirement> = DEFAULT_PASSWORD_REQUIREMENTS

    private suspend fun existsLegacyByUsername(username: String): Boolean {
        val hash = MessageDigest.getInstance("SHA-256").digest(username.toByteArray())
        return provider.newSuspendTransaction { LegacyAccountTable.exists { LegacyAccountTable.usernameHash eq hash } }
    }

    private fun List<StringRequirement>.reservedRequirement(): StringRequirement.Reserved =
        filterIsInstance<StringRequirement.Reserved>().first()

    private fun org.jetbrains.exposed.v1.core.ResultRow.toAccount() =
        Account(
            id = this[AccountTable.id].value,
            username = this[AccountTable.username],
            discord = this[AccountTable.discord],
            games = this[AccountTable.games],
            playtime = this[AccountTable.playtime],
            creation = this[AccountTable.creation],
            legacy = this[AccountTable.legacy],
            rank = this[AccountTable.rank],
        )

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}
