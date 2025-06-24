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

import com.xpdustry.imperium.common.application.BaseImperiumApplication
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.hash.GenericSaltyHashFunction
import com.xpdustry.imperium.common.hash.ShaHashFunction
import com.xpdustry.imperium.common.hash.ShaType
import com.xpdustry.imperium.common.inject.MutableInstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.provider
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.TestMessenger
import com.xpdustry.imperium.common.misc.exists
import com.xpdustry.imperium.common.registerCommonModule
import java.net.InetAddress
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
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
    private lateinit var manager: SimpleAccountManager

    @BeforeEach
    fun init() {
        application = BaseImperiumApplication(LoggerFactory.getLogger(this::class.java))
        application.instances.registerCommonModule()
        application.instances.registerAccountTestModule()
        manager = application.instances.get<AccountManager>() as SimpleAccountManager
        application.init()
    }

    @AfterEach
    fun exit() {
        application.exit(ExitStatus.EXIT)
    }

    @Test
    fun `test simple registration`() = runTest {
        val username = randomUsername()

        assertInstanceOf(AccountResult.InvalidPassword::class.java, manager.register(username, INVALID_PASSWORD))

        assertEquals(AccountResult.Success, manager.register(username, TEST_PASSWORD_1))

        assertEquals(AccountResult.AlreadyRegistered, manager.register(username, TEST_PASSWORD_1))

        val account = manager.selectByUsername(username)
        assertNotNull(account)
        assertEquals(username, account!!.username)
    }

    @Test
    fun `test find by discord`() = runTest {
        val username = randomUsername()
        val discord = Random.nextLong()

        assertFalse(manager.updateDiscord(10, discord))

        assertEquals(AccountResult.Success, manager.register(username, TEST_PASSWORD_1))

        val account = manager.selectByUsername(username)!!
        assertNull(manager.selectByDiscord(discord))

        assertTrue(manager.updateDiscord(account.id, discord))

        val result = manager.selectByDiscord(discord)
        assertEquals(discord, result?.discord)
        assertEquals(account.copy(discord = discord), result)
    }

    @Test
    fun `test session flow`() = runTest {
        val username = randomUsername()
        val sessionKey = randomSessionKey()

        assertFalse(manager.logout(sessionKey))

        assertEquals(AccountResult.NotFound, manager.login(sessionKey, username, TEST_PASSWORD_1))

        assertEquals(AccountResult.Success, manager.register(username, TEST_PASSWORD_1))

        assertEquals(AccountResult.NotFound, manager.login(sessionKey, username, TEST_PASSWORD_2))

        assertEquals(AccountResult.Success, manager.login(sessionKey, username, TEST_PASSWORD_1))

        val account = manager.selectByUsername(username)
        assertNotNull(account)
        assertEquals(account, manager.selectBySession(sessionKey))

        assertTrue(manager.logout(sessionKey))

        assertNull(manager.selectBySession(sessionKey))
    }

    @Test
    fun `test change password`() = runTest {
        val username = randomUsername()
        val sessionKey = randomSessionKey()

        assertEquals(AccountResult.NotFound, manager.updatePassword(1, TEST_PASSWORD_1, TEST_PASSWORD_2))

        assertEquals(AccountResult.Success, manager.register(username, TEST_PASSWORD_1))
        val account = manager.selectByUsername(username)!!.id
        assertEquals(AccountResult.Success, manager.login(sessionKey, username, TEST_PASSWORD_1))

        assertInstanceOf(
            AccountResult.InvalidPassword::class.java,
            manager.updatePassword(account, TEST_PASSWORD_1, INVALID_PASSWORD),
        )

        assertEquals(AccountResult.WrongPassword, manager.updatePassword(account, TEST_PASSWORD_2, TEST_PASSWORD_1))

        assertEquals(AccountResult.Success, manager.updatePassword(account, TEST_PASSWORD_1, TEST_PASSWORD_2))

        assertTrue(manager.logout(sessionKey))
        assertEquals(AccountResult.Success, manager.login(sessionKey, username, TEST_PASSWORD_2))
    }

    @Test
    fun `test migrate`() = runTest {
        val takenUsername = randomUsername()
        val oldUsername = randomUsername()
        val newUsername = randomUsername()
        val games = 10
        val playtime = Duration.ofHours(10L)
        val achievements = listOf(Achievement.ACTIVE, Achievement.MONTH)

        assertEquals(AccountResult.NotFound, manager.migrate(oldUsername, newUsername, TEST_PASSWORD_1))

        val provider = application.instances.get<SQLProvider>()
        val id =
            provider.newSuspendTransaction {
                val hashedUsername = ShaHashFunction.create(oldUsername.toCharArray(), ShaType.SHA256).hash
                val hashedPassword =
                    GenericSaltyHashFunction.create(TEST_PASSWORD_1, SimpleAccountManager.LEGACY_PASSWORD_PARAMS)

                val id =
                    LegacyAccountTable.insertAndGetId {
                        it[usernameHash] = hashedUsername
                        it[passwordHash] = hashedPassword.hash
                        it[passwordSalt] = hashedPassword.salt
                        it[LegacyAccountTable.games] = games
                        it[LegacyAccountTable.playtime] = playtime
                        it[rank] = Rank.OVERSEER
                    }

                LegacyAccountAchievementTable.batchInsert(achievements) {
                    this[LegacyAccountAchievementTable.account] = id
                    this[LegacyAccountAchievementTable.achievement] = it
                }

                id
            }

        assertEquals(AccountResult.Success, manager.register(takenUsername, TEST_PASSWORD_1))
        assertInstanceOf(
            AccountResult.InvalidUsername::class.java,
            manager.migrate(oldUsername, "XX_epyc_gaymer_XX", TEST_PASSWORD_1),
        )
        assertEquals(AccountResult.AlreadyRegistered, manager.migrate(oldUsername, takenUsername, TEST_PASSWORD_1))
        assertEquals(AccountResult.NotFound, manager.migrate(newUsername, oldUsername, TEST_PASSWORD_1))
        assertEquals(AccountResult.NotFound, manager.migrate(oldUsername, newUsername, TEST_PASSWORD_2))

        assertEquals(AccountResult.Success, manager.migrate(oldUsername, newUsername, TEST_PASSWORD_1))

        assertFalse(provider.newSuspendTransaction { LegacyAccountTable.exists { LegacyAccountTable.id eq id } })
    }

    private fun randomSessionKey() = SessionKey(Random.nextLong(), Random.nextLong(), InetAddress.getLoopbackAddress())

    private fun randomUsername(): String {
        val chars = CharArray(16)
        for (i in chars.indices) {
            chars[i] = Random.nextInt('a'.code, 'z'.code).toChar()
        }
        return String(chars)
    }

    private fun MutableInstanceManager.registerAccountTestModule() {
        provider<ImperiumConfig> {
            ImperiumConfig(database = DatabaseConfig.H2(memory = true, database = UUID.randomUUID().toString()))
        }
        provider<Messenger> { TestMessenger() }
        provider<Path>("directory") { tempDir }
    }

    companion object {
        private val TEST_PASSWORD_1 = "ABc123!#".toCharArray()
        private val TEST_PASSWORD_2 = "123ABc!#".toCharArray()
        private val INVALID_PASSWORD = "1234".toCharArray()
    }
}
