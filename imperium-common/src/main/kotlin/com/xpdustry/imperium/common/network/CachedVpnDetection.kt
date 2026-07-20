// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.network

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import java.net.InetAddress
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

class CachedVpnDetection(private val delegate: VpnDetection, private val provider: SQLProvider) :
    VpnDetection, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.create(VpnDetectionCacheTable) }
    }

    override suspend fun isVpn(address: InetAddress): VpnDetection.Result {
        val cached =
            provider.newSuspendTransaction {
                VpnDetectionCacheTable.select(VpnDetectionCacheTable.vpn)
                    .where {
                        (VpnDetectionCacheTable.address eq address.address) and
                            (VpnDetectionCacheTable.updatedAt greaterEq Clock.System.now() - CACHE_TTL)
                    }
                    .firstOrNull()
                    ?.get(VpnDetectionCacheTable.vpn)
            }
        if (cached != null) {
            return VpnDetection.Result.Success(cached)
        }

        return delegate.isVpn(address).also { result ->
            if (result is VpnDetection.Result.Success) {
                provider.newSuspendTransaction {
                    VpnDetectionCacheTable.upsert {
                        it[VpnDetectionCacheTable.address] = address.address
                        it[vpn] = result.vpn
                        it[updatedAt] = Clock.System.now()
                    }
                }
            }
        }
    }

    private companion object {
        val CACHE_TTL = 24.hours
    }
}

private object VpnDetectionCacheTable : Table("vpn_detection_cache") {
    val address = binary("address", 16)
    val vpn = bool("vpn")
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(address)
}
