// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import org.jetbrains.exposed.v1.core.Table

const val MAX_WHITELIST_REASON_LENGTH = 256

object AddressWhitelistTable : Table("address_whitelist") {
    val address = binary("address", 16)
    val reason = varchar("reason", MAX_WHITELIST_REASON_LENGTH).default("Unknown")
    override val primaryKey = PrimaryKey(address)
}

object UuidWhitelistTable : Table("uuid_whitelist") {
    val uuid = long("uuid")
    val reason = varchar("reason", MAX_WHITELIST_REASON_LENGTH).default("Unknown")
    override val primaryKey = PrimaryKey(uuid)
}
