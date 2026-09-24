// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.exists
import com.xpdustry.imperium.common.misc.isCRC32Muuid
import com.xpdustry.imperium.common.misc.toCRC32Muuid
import com.xpdustry.imperium.common.misc.toInetAddressOrNull
import com.xpdustry.imperium.common.misc.toLongMuuid
import java.net.InetAddress
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

sealed interface WhitelistTarget {
    data class Address(val address: InetAddress) : WhitelistTarget {
        override fun toString(): String = address.hostAddress
    }

    data class Uuid(val uuid: MindustryUUID) : WhitelistTarget {
        override fun toString(): String = uuid
    }

    companion object {
        fun parse(input: String): WhitelistTarget? =
            if (input.isCRC32Muuid()) Uuid(input) else input.toInetAddressOrNull()?.let(::Address)
    }
}

data class WhitelistEntry(val target: WhitelistTarget, val reason: String)

interface Whitelist {
    suspend fun add(target: WhitelistTarget, reason: String)

    suspend fun remove(target: WhitelistTarget): Boolean

    /** Whether the player is whitelisted, either by its address or its uuid. */
    suspend fun contains(address: InetAddress, uuid: MindustryUUID): Boolean

    suspend fun list(): List<WhitelistEntry>
}

@Inject
class SimpleWhitelist(private val provider: SQLProvider) : Whitelist, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.create(AddressWhitelistTable, UuidWhitelistTable) }
    }

    override suspend fun add(target: WhitelistTarget, reason: String): Unit = provider.newSuspendTransaction {
        when (target) {
            is WhitelistTarget.Address ->
                AddressWhitelistTable.upsert {
                    it[address] = target.address.address
                    it[AddressWhitelistTable.reason] = reason
                }
            is WhitelistTarget.Uuid ->
                UuidWhitelistTable.upsert {
                    it[uuid] = target.uuid.toLongMuuid()
                    it[UuidWhitelistTable.reason] = reason
                }
        }
    }

    override suspend fun remove(target: WhitelistTarget): Boolean = provider.newSuspendTransaction {
        when (target) {
            is WhitelistTarget.Address -> AddressWhitelistTable.deleteWhere { address eq target.address.address }
            is WhitelistTarget.Uuid -> UuidWhitelistTable.deleteWhere { uuid eq target.uuid.toLongMuuid() }
        } > 0
    }

    override suspend fun contains(address: InetAddress, uuid: MindustryUUID): Boolean = provider.newSuspendTransaction {
        UuidWhitelistTable.exists { UuidWhitelistTable.uuid eq uuid.toLongMuuid() } ||
            AddressWhitelistTable.exists { AddressWhitelistTable.address eq address.address }
    }

    // Stable ordering is required since the list is paginated
    override suspend fun list(): List<WhitelistEntry> = provider.newSuspendTransaction {
        val addresses =
            AddressWhitelistTable.selectAll().orderBy(AddressWhitelistTable.address).map {
                WhitelistEntry(
                    WhitelistTarget.Address(InetAddress.getByAddress(it[AddressWhitelistTable.address])),
                    it[AddressWhitelistTable.reason],
                )
            }
        val uuids =
            UuidWhitelistTable.selectAll().orderBy(UuidWhitelistTable.uuid).map {
                WhitelistEntry(
                    WhitelistTarget.Uuid(it[UuidWhitelistTable.uuid].toCRC32Muuid()),
                    it[UuidWhitelistTable.reason],
                )
            }
        addresses + uuids
    }
}
