// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.content

import com.xpdustry.imperium.common.application.BaseImperiumApplication
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.DependencyService
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.registerCommonModule
import java.nio.file.Path
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory

class SimpleMindustryMapManagerTest {
    @TempDir private lateinit var tempDir: Path
    private val database = UUID.randomUUID().toString()
    private lateinit var application: BaseImperiumApplication
    private lateinit var maps: MindustryMapManager

    @AfterEach
    fun exit() {
        if (::application.isInitialized) application.exit(ExitStatus.EXIT)
    }

    @Test
    fun `test find recently played maps`() = runTest {
        start()
        val a = createMap("a")
        val b = createMap("b")
        val c = createMap("c")

        // Start times are deliberately out of order, the games are ordered by when they were recorded
        addGame(a, "survival", 5)
        addGame(b, "survival", 1)
        addGame(c, "attack", 3)
        addGame(a, "survival", 2)
        addGame(c, "survival", 4)

        assertEquals(listOf(c, a, b, a), maps.findRecentlyPlayedMaps("survival", 10))
        assertEquals(listOf(c, a), maps.findRecentlyPlayedMaps("survival", 2))
        assertEquals(listOf(c), maps.findRecentlyPlayedMaps("attack", 10))
        assertEquals(emptyList<Int>(), maps.findRecentlyPlayedMaps("pvp", 10))
    }

    private suspend fun createMap(name: String) =
        maps.createMap(name, null, null, 10, 10) { ByteArray(0).inputStream() }

    private suspend fun addGame(map: Int, server: String, start: Int) =
        maps.addMapGame(
            map,
            MindustryMap.PlayThrough.Data(
                server = server,
                start = Instant.fromEpochSeconds(start * 3600L),
                playtime = 10.minutes,
                unitsCreated = 0,
                ennemiesKilled = 0,
                wavesLasted = 0,
                buildingsConstructed = 0,
                buildingsDeconstructed = 0,
                buildingsDestroyed = 0,
                winner = 0U,
            ),
        )

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
        maps = application.instances.get()
        application.init()
    }

    private fun DependencyService.Binder.registerTestModule() {
        bindToProv<ImperiumConfig> { ImperiumConfig(database = DatabaseConfig.H2(memory = true, database = database)) }
        bindToProv<MessageService> { MessageService.Noop }
        bindToProv<Path>("directory") { tempDir }
    }
}
