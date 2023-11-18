/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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

import com.xpdustry.imperium.common.snowflake.SnowflakeIdTable
import java.time.Duration
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.duration
import org.jetbrains.exposed.sql.javatime.timestamp

object AccountTable : SnowflakeIdTable("account") {
    val username = varchar("username", 32).uniqueIndex()
    val passwordHash = binary("password_hash", 64)
    val passwordSalt = binary("password_salt", 64)
    val discord = long("discord").nullable().uniqueIndex().default(null)
    val games = integer("games").default(0)
    val playtime = duration("playtime").default(Duration.ZERO)
    val legacy = bool("legacy").default(false)
}

object AccountSessionTable : Table("account_user_session") {
    val account = reference("account_id", AccountTable, onDelete = ReferenceOption.CASCADE)
    val hash = binary("hash", 64).uniqueIndex()
    val expiration = timestamp("expiration")
    override val primaryKey = PrimaryKey(account, hash)
}

object AccountAchievementTable : Table("account_achievement") {
    val account = reference("account_id", AccountTable, onDelete = ReferenceOption.CASCADE)
    val achievement = enumerationByName<Account.Achievement>("achievement", 32)
    val progress = integer("progress").default(0)
    val completed = bool("completed").default(false)
    override val primaryKey = PrimaryKey(account, achievement)
}

object LegacyAccountTable : IntIdTable("account_legacy") {
    val usernameHash = binary("username_hash", 32).uniqueIndex()
    val passwordHash = binary("password_hash", 64)
    val passwordSalt = binary("password_salt", 64)
    val games = integer("games")
    val playtime = duration("playtime")
}

object LegacyAccountAchievementTable : Table("account_legacy_achievement") {
    val account =
        reference("legacy_account_id", LegacyAccountTable, onDelete = ReferenceOption.CASCADE)
    val achievement = enumerationByName<Account.Achievement>("achievement", 32)
    override val primaryKey = PrimaryKey(account, achievement)
}
