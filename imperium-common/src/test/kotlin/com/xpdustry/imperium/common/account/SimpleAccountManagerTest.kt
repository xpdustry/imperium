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
    private lateinit var manager: SimpleAccountManager

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
