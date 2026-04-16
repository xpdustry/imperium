// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import java.time.Duration
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.duration
import org.jetbrains.exposed.v1.javatime.timestamp

object AccountTable : IntIdTable("account") {
    val username = varchar("username", 32).uniqueIndex()
    val passwordHash = binary("password_hash", 64)
    val passwordSalt = binary("password_salt", 64)
    val discord = long("discord").nullable().default(null)
    val games = integer("games").default(0)
    val playtime = duration("playtime").default(Duration.ZERO)
    val legacy = bool("legacy").default(false)
    val rank = enumerationByName<Rank>("rank", 32).default(Rank.EVERYONE)
    val creation = timestamp("creation").defaultExpression(CurrentTimestamp)
}

object AccountSessionTable : Table("account_session") {
    val account = reference("account_id", AccountTable, onDelete = ReferenceOption.CASCADE)
    val uuid = long("uuid")
    val usid = long("usid")
    val address = binary("address", 16)
    val server = varchar("server", 32).default("")
    val creation = timestamp("creation").defaultExpression(CurrentTimestamp)
    val lastLogin = timestamp("last_login").defaultExpression(CurrentTimestamp)
    val expiration = timestamp("expiration").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(uuid, usid, address)
}

object AccountAchievementTable : Table("account_achievement") {
    val account = reference("account_id", AccountTable, onDelete = ReferenceOption.CASCADE)
    val achievement = enumerationByName<Achievement>("achievement", 32)
    val completed = bool("completed").default(false)
    override val primaryKey = PrimaryKey(account, achievement)
}

object AccountMetadataTable : Table("account_metadata") {
    val account = reference("account_id", AccountTable, onDelete = ReferenceOption.CASCADE)
    val key = varchar("key", 64)
    val value = text("value")
    override val primaryKey = PrimaryKey(account, key)
}

object LegacyAccountTable : IntIdTable("legacy_account") {
    val usernameHash = binary("username_hash", 32).uniqueIndex()
    val passwordHash = binary("password_hash", 32)
    val passwordSalt = binary("password_salt", 16)
    val games = integer("games").default(0)
    val playtime = duration("playtime").default(Duration.ZERO)
    val rank = enumerationByName<Rank>("rank", 32)
}

object LegacyAccountAchievementTable : Table("legacy_account_achievement") {
    val account = reference("legacy_account_id", LegacyAccountTable, onDelete = ReferenceOption.CASCADE)
    val achievement = enumerationByName<Achievement>("achievement", 32)
    override val primaryKey = PrimaryKey(account, achievement)
}
