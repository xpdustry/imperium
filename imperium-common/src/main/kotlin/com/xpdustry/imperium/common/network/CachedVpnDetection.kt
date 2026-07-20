// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.network

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import java.net.InetAddress
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

private val SUCCESS_CACHE_TTL = 24.hours
private val FAILURE_CACHE_TTL = 1.hours
private const val FAILURE_MESSAGE_LENGTH = 512

class CachedVpnDetection(private val delegate: VpnDetection, private val provider: SQLProvider) :
    VpnDetection, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.createMissingTablesAndColumns(VpnDetectionCacheTable) }
    }

    override suspend fun isVpn(address: InetAddress): VpnDetection.Result {
        fetchFromCache(address)?.let {
            return it
        }

        return provider.newSuspendTransaction {
            val now = Clock.System.now()
            // Ensure a row exists to lock. Concurrent inserts for the same address are serialized by the primary key.
            VpnDetectionCacheTable.insertIgnore {
                it[VpnDetectionCacheTable.address] = address.address
                it[vpn] = false
                it[status] = CacheStatus.FAILURE
                it[updatedAt] = now - SUCCESS_CACHE_TTL
            }

            val cached =
                // Keep this lock through the upstream request so other servers wait, then reuse the stored result.
                VpnDetectionCacheTable.select(VpnDetectionCacheTable.fields)
                    .where { VpnDetectionCacheTable.address eq address.address }
                    .forUpdate()
                    .single()
                    .toCachedResult(now)
            if (cached != null) {
                return@newSuspendTransaction cached
            }

            val result =
                try {
                    delegate.isVpn(address)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    VpnDetection.Result.Failure(error)
                }
            VpnDetectionCacheTable.update({ VpnDetectionCacheTable.address eq address.address }) {
                it[vpn] = (result as? VpnDetection.Result.Success)?.vpn ?: false
                it[status] = result.toCacheStatus()
                it[failure] = (result as? VpnDetection.Result.Failure)?.exception?.message?.take(FAILURE_MESSAGE_LENGTH)
                it[updatedAt] = Clock.System.now()
            }
            result
        }
    }

    private suspend fun fetchFromCache(address: InetAddress): VpnDetection.Result? =
        provider.newSuspendTransaction {
            VpnDetectionCacheTable.select(VpnDetectionCacheTable.fields)
                .where { VpnDetectionCacheTable.address eq address.address }
                .firstOrNull()
                ?.toCachedResult(Clock.System.now())
        }
}

private enum class CacheStatus {
    SUCCESS,
    FAILURE,
    RATE_LIMITED,
}

private object VpnDetectionCacheTable : Table("vpn_detection_cache") {
    val address = binary("address", 16)
    val vpn = bool("vpn")
    val status = enumerationByName<CacheStatus>("status", 16).default(CacheStatus.SUCCESS)
    val failure = varchar("failure", FAILURE_MESSAGE_LENGTH).nullable()
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(address)
}

private fun ResultRow.toCachedResult(now: Instant): VpnDetection.Result? {
    val status = this[VpnDetectionCacheTable.status]
    val ttl = if (status == CacheStatus.SUCCESS) SUCCESS_CACHE_TTL else FAILURE_CACHE_TTL
    if (this[VpnDetectionCacheTable.updatedAt] + ttl < now) {
        return null
    }
    return when (status) {
        CacheStatus.SUCCESS -> VpnDetection.Result.Success(this[VpnDetectionCacheTable.vpn])
        CacheStatus.FAILURE ->
            VpnDetection.Result.Failure(
                CachedVpnDetectionException(this[VpnDetectionCacheTable.failure] ?: "Unknown provider failure")
            )
        CacheStatus.RATE_LIMITED -> VpnDetection.Result.RateLimited
    }
}

private fun VpnDetection.Result.toCacheStatus() =
    when (this) {
        is VpnDetection.Result.Success -> CacheStatus.SUCCESS
        is VpnDetection.Result.Failure -> CacheStatus.FAILURE
        VpnDetection.Result.RateLimited -> CacheStatus.RATE_LIMITED
    }

private class CachedVpnDetectionException(message: String) : Exception("Cached VPN detection failure: $message")
