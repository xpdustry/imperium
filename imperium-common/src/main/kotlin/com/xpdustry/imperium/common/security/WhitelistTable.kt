// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object WhitelistTable : IntIdTable("uuid_whitelist") {
    val uuid = long("uuid").uniqueIndex()
    val reason = varchar("reason", 256).default("Unknown")
}