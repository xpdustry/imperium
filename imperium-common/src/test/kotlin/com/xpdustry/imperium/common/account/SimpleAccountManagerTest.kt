// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.application.BaseImperiumApplication
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.DependencyService
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.TestMessenger
import com.xpdustry.imperium.common.registerCommonModule
import com.xpdustry.imperium.common.string.Password
import java.net.InetAddress
import java.nio.file.Path
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
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
