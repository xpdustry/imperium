// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.misc.exists
import java.net.InetAddress
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.upsert

interface AddressWhitelist {
    suspend fun addAddress(address: InetAddress, reason: String)

    suspend fun containsAddress(address: InetAddress): Boolean

    suspend fun removeAddress(address: InetAddress)

    suspend fun listAdresses(): List<AddressWithReason>
}

typealias AddressWithReason = Pair<InetAddress, String>

class SimpleAddressWhitelist(private val provider: SQLProvider) : AddressWhitelist, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.create(AddressWhitelistTable) }
    }

    override suspend fun addAddress(address: InetAddress, reason: String): Unit =
        provider.newSuspendTransaction {
            AddressWhitelistTable.upsert {
                it[AddressWhitelistTable.address] = address.address
                it[AddressWhitelistTable.reason] = reason
            }
        }

    override suspend fun containsAddress(address: InetAddress) =
        provider.newSuspendTransaction {
            AddressWhitelistTable.exists { AddressWhitelistTable.address eq address.address }
        }

    override suspend fun removeAddress(address: InetAddress): Unit =
        provider.newSuspendTransaction {
            AddressWhitelistTable.deleteWhere { AddressWhitelistTable.address eq address.address }
        }

    override suspend fun listAdresses() =
        provider.newSuspendTransaction {
            AddressWhitelistTable.select(AddressWhitelistTable.address, AddressWhitelistTable.reason).map {
                InetAddress.getByAddress(it[AddressWhitelistTable.address]) to it[AddressWhitelistTable.reason]
            }
        }
}
