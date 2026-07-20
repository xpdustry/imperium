// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.network

import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.database.SimpleSQLProvider
import java.net.InetAddress
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.time.Duration.Companion.milliseconds

class CachedVpnDetectionTest {
    @TempDir private lateinit var tempDir: Path
    private lateinit var databaseConfig: DatabaseConfig.H2
    private lateinit var provider: SimpleSQLProvider

    @BeforeEach
    fun init() {
        databaseConfig = DatabaseConfig.H2(memory = true, database = UUID.randomUUID().toString())
        provider = SimpleSQLProvider(databaseConfig, tempDir)
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
        assertEquals(1, delegate.calls.get())

        val restartedDelegate = TestVpnDetection(VpnDetection.Result.Success(false))
        val restarted = CachedVpnDetection(restartedDelegate, provider)
        restarted.onImperiumInit()

        assertTrue((restarted.isVpn(address) as VpnDetection.Result.Success).vpn)
        assertEquals(0, restartedDelegate.calls.get())
    }

    @Test
    fun `non vpn results are persisted`() = runBlocking {
        val address = InetAddress.getByName("203.0.113.3")
        val delegate = TestVpnDetection(VpnDetection.Result.Success(false))
        val cache = CachedVpnDetection(delegate, provider)
        cache.onImperiumInit()

        assertFalse((cache.isVpn(address) as VpnDetection.Result.Success).vpn)
        assertFalse((cache.isVpn(address) as VpnDetection.Result.Success).vpn)
        assertEquals(1, delegate.calls.get())
    }

    @Test
    fun `failures are cached for one hour`() = runBlocking {
        val address = InetAddress.getByName("203.0.113.2")
        val delegate = TestVpnDetection(VpnDetection.Result.Failure(IllegalStateException("failed")))
        val cache = CachedVpnDetection(delegate, provider)
        cache.onImperiumInit()

        assertFalse(cache.isVpn(address) is VpnDetection.Result.Success)
        assertFalse(cache.isVpn(address) is VpnDetection.Result.Success)
        assertEquals(1, delegate.calls.get())
    }

    @Test
    fun `thrown exceptions are cached`() = runBlocking {
        val address = InetAddress.getByName("203.0.113.4")
        val delegate = ThrowingVpnDetection()
        val cache = CachedVpnDetection(delegate, provider)
        cache.onImperiumInit()

        assertFalse(cache.isVpn(address) is VpnDetection.Result.Success)
        assertFalse(cache.isVpn(address) is VpnDetection.Result.Success)
        assertEquals(1, delegate.calls.get())
    }

    @Test
    fun `rate limits are cached`() = runBlocking {
        val address = InetAddress.getByName("203.0.113.6")
        val delegate = TestVpnDetection(VpnDetection.Result.RateLimited)
        val cache = CachedVpnDetection(delegate, provider)
        cache.onImperiumInit()

        assertTrue(cache.isVpn(address) is VpnDetection.Result.RateLimited)
        assertTrue(cache.isVpn(address) is VpnDetection.Result.RateLimited)
        assertEquals(1, delegate.calls.get())
    }

    @Test
    fun `concurrent misses are coalesced through the database`() = runBlocking {
        val secondProvider = SimpleSQLProvider(databaseConfig, tempDir)
        secondProvider.onImperiumInit()
        try {
            val address = InetAddress.getByName("203.0.113.5")
            val delegate = DelayedVpnDetection()
            val first = CachedVpnDetection(delegate, provider)
            val second = CachedVpnDetection(delegate, secondProvider)
            first.onImperiumInit()
            second.onImperiumInit()

            coroutineScope {
                List(20) { index ->
                        async(Dispatchers.Default) { (if (index % 2 == 0) first else second).isVpn(address) }
                    }
                    .awaitAll()
                    .forEach { assertFalse((it as VpnDetection.Result.Success).vpn) }
            }

            assertEquals(1, delegate.calls.get())
        } finally {
            secondProvider.onImperiumExit()
        }
    }

    private class TestVpnDetection(private val result: VpnDetection.Result) : VpnDetection {
        val calls = AtomicInteger()

        override suspend fun isVpn(address: InetAddress): VpnDetection.Result {
            calls.incrementAndGet()
            return result
        }
    }

    private class ThrowingVpnDetection : VpnDetection {
        val calls = AtomicInteger()

        override suspend fun isVpn(address: InetAddress): VpnDetection.Result {
            calls.incrementAndGet()
            throw IllegalStateException("failed")
        }
    }

    private class DelayedVpnDetection : VpnDetection {
        val calls = AtomicInteger()

        override suspend fun isVpn(address: InetAddress): VpnDetection.Result {
            calls.incrementAndGet()
            delay(100.milliseconds)
            return VpnDetection.Result.Success(false)
        }
    }
}
