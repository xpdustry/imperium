// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object AddressWhitelistTable : Table("address_whitelist") {
    val address: Column<ByteArray> = binary("address", 16)
    val reason = varchar("reason", 256).default("Unknown")
    override val primaryKey = PrimaryKey(address)
}
