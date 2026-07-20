// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.network

import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.database.SimpleSQLProvider
import java.net.InetAddress
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CachedVpnDetectionTest {
    @TempDir private lateinit var tempDir: Path
    private lateinit var provider: SimpleSQLProvider

    @BeforeEach
    fun init() {
        provider = SimpleSQLProvider(DatabaseConfig.H2(memory = true, database = UUID.randomUUID().toString()), tempDir)
        provider.onImperiumInit()
    }

    @AfterEach
    fun exit() {
        provider.onImperiumExit()
    }

    @Test
    fun `successful results are persisted`() = runBlocking {
        val address = InetAddress.getByName("203.0.113.1")
        val delegate = TestVpnDetection(VpnDetection.Result.Success(true))
        val first = CachedVpnDetection(delegate, provider)
        first.onImperiumInit()

        assertTrue((first.isVpn(address) as VpnDetection.Result.Success).vpn)
        assertEquals(1, delegate.calls)

        val restartedDelegate = TestVpnDetection(VpnDetection.Result.Success(false))
        val restarted = CachedVpnDetection(restartedDelegate, provider)
        restarted.onImperiumInit()

        assertTrue((restarted.isVpn(address) as VpnDetection.Result.Success).vpn)
        assertEquals(0, restartedDelegate.calls)
    }

    @Test
    fun `non vpn results are persisted`() = runBlocking {
        val address = InetAddress.getByName("203.0.113.3")
        val delegate = TestVpnDetection(VpnDetection.Result.Success(false))
        val cache = CachedVpnDetection(delegate, provider)
        cache.onImperiumInit()

        assertFalse((cache.isVpn(address) as VpnDetection.Result.Success).vpn)
        assertFalse((cache.isVpn(address) as VpnDetection.Result.Success).vpn)
        assertEquals(1, delegate.calls)
    }

    @Test
    fun `failures are not cached`() = runBlocking {
        val address = InetAddress.getByName("203.0.113.2")
        val delegate = TestVpnDetection(VpnDetection.Result.Failure(IllegalStateException("failed")))
        val cache = CachedVpnDetection(delegate, provider)
        cache.onImperiumInit()

        assertFalse(cache.isVpn(address) is VpnDetection.Result.Success)
        assertFalse(cache.isVpn(address) is VpnDetection.Result.Success)
        assertEquals(2, delegate.calls)
    }

    private class TestVpnDetection(private val result: VpnDetection.Result) : VpnDetection {
        var calls = 0

        override suspend fun isVpn(address: InetAddress): VpnDetection.Result {
            calls++
            return result
        }
    }
}
