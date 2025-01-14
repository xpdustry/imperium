/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.common.account

import java.time.Duration
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.duration
import org.jetbrains.exposed.sql.javatime.timestamp

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
    val expiration = timestamp("creation")
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
    val key = varchar("key", 32)
    val value = varchar("value", 64)
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
