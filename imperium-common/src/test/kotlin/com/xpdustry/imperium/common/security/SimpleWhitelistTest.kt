// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.application.BaseImperiumApplication
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.DependencyService
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.TestMessenger
import com.xpdustry.imperium.common.misc.toCRC32Muuid
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.registerCommonModule
import java.nio.file.Path
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory

class SimpleWhitelistTest {
    @TempDir private lateinit var tempDir: Path
    private val database = UUID.randomUUID().toString()
    private lateinit var application: BaseImperiumApplication
    private lateinit var whitelist: Whitelist

    @AfterEach
    fun exit() {
        if (::application.isInitialized) application.exit(ExitStatus.EXIT)
    }

    @Test
    fun `test address and uuid entries`() = runTest {
        start()
        val address = "1.2.3.4".toInetAddress()
        val uuid = randomUuid()

        assertFalse(whitelist.contains(address, uuid))

        whitelist.add(WhitelistTarget.Address(address), "address")
        whitelist.add(WhitelistTarget.Uuid(uuid), "uuid")
        whitelist.add(WhitelistTarget.Uuid(uuid), "updated")

        assertTrue(whitelist.contains(address, randomUuid()))
        assertTrue(whitelist.contains("5.6.7.8".toInetAddress(), uuid))
        assertFalse(whitelist.contains("5.6.7.8".toInetAddress(), randomUuid()))
        assertEquals(
            listOf(
                WhitelistEntry(WhitelistTarget.Address(address), "address"),
                WhitelistEntry(WhitelistTarget.Uuid(uuid), "updated"),
            ),
            whitelist.list(),
        )

        assertTrue(whitelist.remove(WhitelistTarget.Uuid(uuid)))
        assertFalse(whitelist.remove(WhitelistTarget.Uuid(uuid)))
        assertFalse(whitelist.contains("5.6.7.8".toInetAddress(), uuid))
        assertTrue(whitelist.contains(address, uuid))
    }

    @Test
    fun `test target parsing`() {
        val uuid = randomUuid()
        assertEquals(WhitelistTarget.Uuid(uuid), WhitelistTarget.parse(uuid))
        assertEquals(WhitelistTarget.Address("1.2.3.4".toInetAddress()), WhitelistTarget.parse("1.2.3.4"))
        assertEquals(WhitelistTarget.Address("::1".toInetAddress()), WhitelistTarget.parse("::1"))
        assertNull(WhitelistTarget.parse("example.com"))
        assertNull(WhitelistTarget.parse("nope"))
    }

    private fun start() {
        application =
            BaseImperiumApplication(
                LoggerFactory.getLogger(this::class.java),
                modules = {
                    registerCommonModule()
                    registerTestModule()
                },
            )
        application.createAll()
        whitelist = application.instances.get()
        application.init()
    }

    private fun randomUuid() = Random.nextBytes(8).toCRC32Muuid()

    private fun DependencyService.Binder.registerTestModule() {
        bindToProv<ImperiumConfig> { ImperiumConfig(database = DatabaseConfig.H2(memory = true, database = database)) }
        bindToProv<MessageService> { TestMessenger() }
        bindToProv<Path>("directory") { tempDir }
    }
}
