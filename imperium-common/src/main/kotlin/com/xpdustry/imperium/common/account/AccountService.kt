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
import java.util.EnumSet
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
        val invalid = validatePassword(password) ?: validateUsername(username)
        if (invalid != null) {
            return invalid
        }

        val hash = ImperiumArgon2.create(password)
        val identifier = provider.newSuspendTransaction {
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
        return updatePassword(account, newPassword)
    }

    /** Sets the password of an account without checking the previous one, meant for administrative purposes. */
    suspend fun updatePassword(account: Int, password: Password): AccountResult {
        validatePassword(password)?.let {
            return it
        }

        val hash = ImperiumArgon2.create(password)
        val updated = provider.newSuspendTransaction {
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

    suspend fun updateUsername(account: Int, username: String): AccountResult {
        validateUsername(username)?.let {
            return it
        }

        val result = provider.newSuspendTransaction {
            val current =
                AccountTable.select(AccountTable.username)
                    .where { AccountTable.id eq account }
                    .firstOrNull()
                    ?.get(AccountTable.username) ?: return@newSuspendTransaction AccountResult.NotFound
            if (current == username) {
                return@newSuspendTransaction AccountResult.Success
            }
            if (AccountTable.exists { AccountTable.username eq username }) {
                return@newSuspendTransaction AccountResult.AlreadyRegistered
            }
            AccountTable.update({ AccountTable.id eq account }) { it[AccountTable.username] = username }
            AccountResult.Success
        }

        if (result == AccountResult.Success) {
            messenger.broadcast(AccountUpdate(account))
        }
        return result
    }

    suspend fun selectById(id: Int): Account? = provider.newSuspendTransaction {
        AccountTable.selectAll().where { AccountTable.id eq id }.firstOrNull()?.toAccount()
    }

    suspend fun existsById(id: Int): Boolean = provider.newSuspendTransaction {
        AccountTable.exists { AccountTable.id eq id }
    }

    suspend fun selectByUsername(username: String): Account? = provider.newSuspendTransaction {
        AccountTable.selectAll().where { AccountTable.username eq username }.firstOrNull()?.toAccount()
    }

    suspend fun existsByUsername(username: String): Boolean = provider.newSuspendTransaction {
        AccountTable.exists { AccountTable.username eq username }
    }

    suspend fun selectByDiscord(discord: Long): Account? = provider.newSuspendTransaction {
        AccountTable.selectAll().where { AccountTable.discord eq discord }.firstOrNull()?.toAccount()
    }

    /**
     * Links the given discord account to an account, or unlinks it if [discord] is null. A discord account can only be
     * linked to a single account, so it is unlinked from any other account beforehand.
     */
    suspend fun updateDiscord(account: Int, discord: Long?): Boolean {
        var previous = emptyList<Int>()
        val updated = provider.newSuspendTransaction {
            if (!AccountTable.exists { AccountTable.id eq account }) {
                return@newSuspendTransaction false
            }
            if (discord != null) {
                previous =
                    AccountTable.select(AccountTable.id)
                        .where { (AccountTable.discord eq discord) and (AccountTable.id neq account) }
                        .map { it[AccountTable.id].value }
                if (previous.isNotEmpty()) {
                    AccountTable.update({ AccountTable.id inList previous }) { it[AccountTable.discord] = null }
                }
            }
            AccountTable.update({ AccountTable.id eq account }) { it[AccountTable.discord] = discord } > 0
        }
        if (updated) {
            previous.forEach { messenger.broadcast(AccountUpdate(it)) }
            messenger.broadcast(AccountUpdate(account))
        }
        return updated
    }

    suspend fun incrementGames(account: Int): Boolean {
        val updated = provider.newSuspendTransaction {
            AccountTable.update({ AccountTable.id eq account }) { it[games] = games.plus(1) } > 0
        }
        if (updated) {
            messenger.broadcast(AccountUpdate(account))
        }
        return updated
    }

    suspend fun incrementPlaytime(account: Int, duration: Duration): Boolean {
        val updated = provider.newSuspendTransaction {
            AccountTable.update({ AccountTable.id eq account }) { it[playtime] = playtime.plus(duration) } > 0
        }
        if (updated) {
            messenger.broadcast(AccountUpdate(account))
        }
        return updated
    }

    suspend fun updateRank(account: Int, rank: Rank): Boolean {
        val changed = provider.newSuspendTransaction {
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

    suspend fun selectPasswordById(id: Int): HashedPassword? = provider.newSuspendTransaction {
        AccountTable.select(AccountTable.passwordHash, AccountTable.passwordSalt)
            .where { AccountTable.id eq id }
            .firstOrNull()
            ?.let { HashedPassword(it[AccountTable.passwordHash], it[AccountTable.passwordSalt]) }
    }

    fun usernameRequirements(): List<StringRequirement> = DEFAULT_USERNAME_REQUIREMENTS

    fun passwordRequirements(): List<StringRequirement> = DEFAULT_PASSWORD_REQUIREMENTS

    suspend fun selectLegacyByUsername(username: String): LegacyAccount? = provider.newSuspendTransaction {
        val row =
            LegacyAccountTable.selectAll()
                .where { LegacyAccountTable.usernameHash eq hashLegacyUsername(username) }
                .firstOrNull() ?: return@newSuspendTransaction null
        val achievements =
            LegacyAccountAchievementTable.select(LegacyAccountAchievementTable.achievement)
                .where { LegacyAccountAchievementTable.account eq row[LegacyAccountTable.id] }
                .mapTo(EnumSet.noneOf(Achievement::class.java)) { it[LegacyAccountAchievementTable.achievement] }
        LegacyAccount(
            id = row[LegacyAccountTable.id].value,
            games = row[LegacyAccountTable.games],
            playtime = row[LegacyAccountTable.playtime],
            rank = row[LegacyAccountTable.rank],
            achievements = achievements,
        )
    }

    /**
     * Converts a legacy account into a regular account named [username], carrying over its games, playtime, rank and
     * achievements. The legacy account is deleted afterward, which also frees its username.
     */
    suspend fun migrateLegacy(legacyUsername: String, username: String, password: Password): AccountResult {
        val invalid = validatePassword(password) ?: validateUsername(username, reservedByLegacy = false)
        if (invalid != null) {
            return invalid
        }

        val hash = ImperiumArgon2.create(password)
        var identifier = -1
        val result = provider.newSuspendTransaction {
            val legacyHash = hashLegacyUsername(legacyUsername)
            val legacy =
                LegacyAccountTable.selectAll().where { LegacyAccountTable.usernameHash eq legacyHash }.firstOrNull()
                    ?: return@newSuspendTransaction AccountResult.NotFound
            if (username != legacyUsername && legacyUsernameExists(username)) {
                return@newSuspendTransaction AccountResult.InvalidUsername(
                    listOf(usernameRequirements().reservedRequirement())
                )
            }
            if (AccountTable.exists { AccountTable.username eq username }) {
                return@newSuspendTransaction AccountResult.AlreadyRegistered
            }

            identifier =
                AccountTable.insert {
                        it[AccountTable.username] = username
                        it[passwordHash] = hash.hash
                        it[passwordSalt] = hash.salt
                        it[games] = legacy[LegacyAccountTable.games]
                        it[playtime] = legacy[LegacyAccountTable.playtime]
                        it[rank] = legacy[LegacyAccountTable.rank]
                        it[AccountTable.legacy] = true
                    }[AccountTable.id]
                    .value

            val achievements =
                LegacyAccountAchievementTable.select(LegacyAccountAchievementTable.achievement)
                    .where { LegacyAccountAchievementTable.account eq legacy[LegacyAccountTable.id] }
                    .map { it[LegacyAccountAchievementTable.achievement] }
            AccountAchievementTable.batchInsert(achievements) { achievement ->
                this[AccountAchievementTable.account] = identifier
                this[AccountAchievementTable.achievement] = achievement
                this[AccountAchievementTable.completed] = true
            }

            LegacyAccountTable.deleteWhere { LegacyAccountTable.id eq legacy[LegacyAccountTable.id] }
            AccountResult.Success
        }

        if (result == AccountResult.Success) {
            messenger.broadcast(AccountUpdate(identifier))
            messenger.broadcast(RankChangeEvent(identifier))
        }
        return result
    }

    suspend fun deleteLegacy(username: String): Boolean = provider.newSuspendTransaction {
        LegacyAccountTable.deleteWhere { usernameHash eq hashLegacyUsername(username) } > 0
    }

    private fun validatePassword(password: Password): AccountResult? =
        passwordRequirements()
            .findMissingRequirements(password.value)
            .takeIf { it.isNotEmpty() }
            ?.let {
                AccountResult.InvalidPassword(it)
            }

    private suspend fun validateUsername(username: String, reservedByLegacy: Boolean = true): AccountResult? {
        val missing = usernameRequirements().findMissingRequirements(username)
        if (missing.isNotEmpty()) {
            return AccountResult.InvalidUsername(missing)
        }
        if (reservedByLegacy && provider.newSuspendTransaction { legacyUsernameExists(username) }) {
            return AccountResult.InvalidUsername(listOf(usernameRequirements().reservedRequirement()))
        }
        return null
    }

    // Must be called within a transaction
    private fun legacyUsernameExists(username: String): Boolean = LegacyAccountTable.exists {
        LegacyAccountTable.usernameHash eq hashLegacyUsername(username)
    }

    private fun hashLegacyUsername(username: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(username.toByteArray())

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
