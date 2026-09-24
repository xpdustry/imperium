// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.application.BaseImperiumApplication
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.dependency.DependencyService
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.TestMessenger
import com.xpdustry.imperium.common.registerCommonModule
import com.xpdustry.imperium.common.string.Password
import com.xpdustry.imperium.common.string.findMissingRequirements
import java.net.InetAddress
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory

class SimpleAccountManagerTest {
    @TempDir private lateinit var tempDir: Path
    private lateinit var application: BaseImperiumApplication
    private lateinit var accounts: AccountService
    private lateinit var sessions: MindustrySessionService
    private lateinit var achievements: AccountAchievementService
    private lateinit var provider: SQLProvider

    @BeforeEach
    fun init() {
        application =
            BaseImperiumApplication(
                LoggerFactory.getLogger(this::class.java),
                modules = {
                    registerCommonModule()
                    registerAccountTestModule()
                },
            )
        application.createAll()
        accounts = application.instances.get()
        sessions = application.instances.get()
        achievements = application.instances.get()
        provider = application.instances.get()
        application.init()
    }

    @AfterEach
    fun exit() {
        application.exit(ExitStatus.EXIT)
    }

    @Test
    fun `test simple registration`() = runTest {
        val username = randomUsername()

        assertInstanceOf(AccountResult.InvalidPassword::class.java, accounts.register(username, INVALID_PASSWORD))

        assertEquals(AccountResult.Success, accounts.register(username, TEST_PASSWORD_1))

        assertEquals(AccountResult.AlreadyRegistered, accounts.register(username, TEST_PASSWORD_1))

        val account = accounts.selectByUsername(username)
        assertNotNull(account)
        assertEquals(username, account!!.username)
    }

    @Test
    fun `test find by discord`() = runTest {
        val username = randomUsername()
        val discord = Random.nextLong()

        assertFalse(accounts.updateDiscord(10, discord))

        assertEquals(AccountResult.Success, accounts.register(username, TEST_PASSWORD_1))

        val account = accounts.selectByUsername(username)!!
        assertNull(accounts.selectByDiscord(discord))

        assertTrue(accounts.updateDiscord(account.id, discord))

        val result = accounts.selectByDiscord(discord)
        assertEquals(discord, result?.discord)
        assertEquals(account.copy(discord = discord), result)
    }

    @Test
    fun `test session flow`() = runTest {
        val username = randomUsername()
        val sessionKey = randomSessionKey()

        assertFalse(sessions.logout(sessionKey))

        assertEquals(AccountResult.NotFound, sessions.login(sessionKey, username, TEST_PASSWORD_1))

        assertEquals(AccountResult.Success, accounts.register(username, TEST_PASSWORD_1))

        assertEquals(AccountResult.NotFound, sessions.login(sessionKey, username, TEST_PASSWORD_2))

        assertEquals(AccountResult.Success, sessions.login(sessionKey, username, TEST_PASSWORD_1))

        val account = accounts.selectByUsername(username)
        assertNotNull(account)
        assertEquals(account, sessions.selectAccount(accounts, sessionKey))

        assertTrue(sessions.logout(sessionKey))

        assertNull(sessions.selectAccount(accounts, sessionKey))
    }

    @Test
    fun `test change password`() = runTest {
        val username = randomUsername()
        val sessionKey = randomSessionKey()

        assertEquals(AccountResult.NotFound, accounts.updatePassword(1, TEST_PASSWORD_1, TEST_PASSWORD_2))

        assertEquals(AccountResult.Success, accounts.register(username, TEST_PASSWORD_1))
        val account = accounts.selectByUsername(username)!!.id
        assertEquals(AccountResult.Success, sessions.login(sessionKey, username, TEST_PASSWORD_1))

        assertInstanceOf(
            AccountResult.InvalidPassword::class.java,
            accounts.updatePassword(account, TEST_PASSWORD_1, INVALID_PASSWORD),
        )

        assertEquals(AccountResult.WrongPassword, accounts.updatePassword(account, TEST_PASSWORD_2, TEST_PASSWORD_1))

        assertEquals(AccountResult.Success, accounts.updatePassword(account, TEST_PASSWORD_1, TEST_PASSWORD_2))

        assertTrue(sessions.logout(sessionKey))
        assertEquals(AccountResult.Success, sessions.login(sessionKey, username, TEST_PASSWORD_2))
    }

    @Test
    fun `test admin password update`() = runTest {
        val username = randomUsername()
        val sessionKey = randomSessionKey()

        assertEquals(AccountResult.NotFound, accounts.updatePassword(1, TEST_PASSWORD_1))

        assertEquals(AccountResult.Success, accounts.register(username, TEST_PASSWORD_1))
        val account = accounts.selectByUsername(username)!!.id
        assertEquals(AccountResult.Success, sessions.login(sessionKey, username, TEST_PASSWORD_1))

        assertInstanceOf(AccountResult.InvalidPassword::class.java, accounts.updatePassword(account, INVALID_PASSWORD))
        assertEquals(AccountResult.Success, accounts.updatePassword(account, TEST_PASSWORD_2))

        assertEquals(1, sessions.logoutAll(account))
        assertNull(sessions.selectAccount(accounts, sessionKey))
        assertEquals(AccountResult.NotFound, sessions.login(sessionKey, username, TEST_PASSWORD_1))
        assertEquals(AccountResult.Success, sessions.login(sessionKey, username, TEST_PASSWORD_2))
    }

    @Test
    fun `test update username`() = runTest {
        val username1 = randomUsername()
        val username2 = randomUsername()
        val username3 = randomUsername()

        assertEquals(AccountResult.NotFound, accounts.updateUsername(1, username1))

        assertEquals(AccountResult.Success, accounts.register(username1, TEST_PASSWORD_1))
        assertEquals(AccountResult.Success, accounts.register(username2, TEST_PASSWORD_1))
        val account = accounts.selectByUsername(username1)!!.id

        assertInstanceOf(AccountResult.InvalidUsername::class.java, accounts.updateUsername(account, "NOPE"))
        assertEquals(AccountResult.AlreadyRegistered, accounts.updateUsername(account, username2))
        assertEquals(AccountResult.Success, accounts.updateUsername(account, username1))

        assertEquals(AccountResult.Success, accounts.updateUsername(account, username3))
        assertNull(accounts.selectByUsername(username1))
        assertEquals(account, accounts.selectByUsername(username3)?.id)
        assertEquals(AccountResult.Success, sessions.login(randomSessionKey(), username3, TEST_PASSWORD_1))
    }

    @Test
    fun `test discord link is unique`() = runTest {
        val username1 = randomUsername()
        val username2 = randomUsername()
        val discord = Random.nextLong()

        assertEquals(AccountResult.Success, accounts.register(username1, TEST_PASSWORD_1))
        assertEquals(AccountResult.Success, accounts.register(username2, TEST_PASSWORD_1))
        val account1 = accounts.selectByUsername(username1)!!.id
        val account2 = accounts.selectByUsername(username2)!!.id

        assertTrue(accounts.updateDiscord(account1, discord))
        assertTrue(accounts.updateDiscord(account2, discord))
        assertNull(accounts.selectById(account1)!!.discord)
        assertEquals(account2, accounts.selectByDiscord(discord)?.id)

        assertTrue(accounts.updateDiscord(account2, null))
        assertNull(accounts.selectByDiscord(discord))
    }

    @Test
    fun `test legacy account migration`() = runTest {
        val legacyUsername = "Legacy_" + randomUsername()
        val username = randomUsername()
        insertLegacyAccount(legacyUsername, Rank.MODERATOR, Achievement.ACTIVE)

        assertEquals(AccountResult.NotFound, accounts.migrateLegacy("unknown", username, TEST_PASSWORD_1))
        assertInstanceOf(
            AccountResult.InvalidPassword::class.java,
            accounts.migrateLegacy(legacyUsername, username, INVALID_PASSWORD),
        )

        val legacy = accounts.selectLegacyByUsername(legacyUsername)
        assertNotNull(legacy)
        assertEquals(Rank.MODERATOR, legacy!!.rank)
        assertEquals(setOf(Achievement.ACTIVE), legacy.achievements)

        assertEquals(AccountResult.Success, accounts.migrateLegacy(legacyUsername, username, TEST_PASSWORD_1))
        assertNull(accounts.selectLegacyByUsername(legacyUsername))

        val account = accounts.selectByUsername(username)!!
        assertTrue(account.legacy)
        assertEquals(Rank.MODERATOR, account.rank)
        assertEquals(42, account.games)
        assertEquals(setOf(Achievement.ACTIVE), achievements.selectAchievements(account.id))
        assertEquals(AccountResult.Success, sessions.login(randomSessionKey(), username, TEST_PASSWORD_1))
    }

    @Test
    fun `test legacy username is reserved`() = runTest {
        val legacyUsername = randomUsername()
        insertLegacyAccount(legacyUsername, Rank.EVERYONE)

        assertInstanceOf(
            AccountResult.InvalidUsername::class.java,
            accounts.register(legacyUsername, TEST_PASSWORD_1),
        )

        // The legacy username can be kept when migrating
        assertEquals(AccountResult.Success, accounts.migrateLegacy(legacyUsername, legacyUsername, TEST_PASSWORD_1))

        val other = randomUsername()
        insertLegacyAccount(other, Rank.EVERYONE)
        assertTrue(accounts.deleteLegacy(other))
        assertFalse(accounts.deleteLegacy(other))
        assertEquals(AccountResult.Success, accounts.register(other, TEST_PASSWORD_1))
    }

    @Test
    fun `test random password satisfies requirements`() {
        repeat(100) {
            val password = Password.random()
            assertEquals(16, password.value.length)
            assertTrue(accounts.passwordRequirements().findMissingRequirements(password.value).isEmpty())
        }
    }

    private fun insertLegacyAccount(username: String, rank: Rank, vararg achievements: Achievement) {
        provider.newTransaction {
            val id = LegacyAccountTable.insertAndGetId {
                it[usernameHash] = MessageDigest.getInstance("SHA-256").digest(username.toByteArray())
                it[passwordHash] = ByteArray(32)
                it[passwordSalt] = ByteArray(16)
                it[games] = 42
                it[LegacyAccountTable.rank] = rank
            }
            LegacyAccountAchievementTable.batchInsert(achievements.toList()) { achievement ->
                this[LegacyAccountAchievementTable.account] = id
                this[LegacyAccountAchievementTable.achievement] = achievement
            }
        }
    }

    private fun randomSessionKey() = SessionKey(Random.nextLong(), Random.nextLong(), InetAddress.getLoopbackAddress())

    private fun randomUsername(): String {
        val chars = CharArray(16)
        for (i in chars.indices) {
            chars[i] = Random.nextInt('a'.code, 'z'.code).toChar()
        }
        return String(chars)
    }

    private fun DependencyService.Binder.registerAccountTestModule() {
        bindToProv<ImperiumConfig> {
            ImperiumConfig(database = DatabaseConfig.H2(memory = true, database = UUID.randomUUID().toString()))
        }
        bindToProv<MessageService> { TestMessenger() }
        bindToProv<Path>("directory") { tempDir }
    }

    companion object {
        private val TEST_PASSWORD_1 = Password("ABc123!#")
        private val TEST_PASSWORD_2 = Password("123ABc!#")
        private val INVALID_PASSWORD = Password("1234")
    }
}
