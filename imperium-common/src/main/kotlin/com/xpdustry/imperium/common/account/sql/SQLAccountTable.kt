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
package com.xpdustry.imperium.common.account.sql

import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.SnowflakeIdTable
import java.time.Duration
import java.time.Instant
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
    val verified = bool("verified").default(false)
}

object AccountSessionTable : Table("account_user_session") {
    val account = reference("account_id", AccountTable, onDelete = ReferenceOption.CASCADE)
    val hash = binary("hash", 64).uniqueIndex()
    val expiration = timestamp("expiration")
    override val primaryKey = PrimaryKey(account, hash)
}

object AccountAchievementTable : Table("account_achievement") {
    val account = reference("account_id", AccountTable, onDelete = ReferenceOption.CASCADE)
    val achievement = enumerationByName<Achievement>("achievement", 32)
    val progress = integer("progress").default(0)
    val completed = bool("completed").default(false)
    override val primaryKey = PrimaryKey(account, achievement)
}

object AccountPermissionTable : Table("account_permission") {
    val account = reference("account_id", AccountTable, onDelete = ReferenceOption.CASCADE)
    val permission = enumerationByName<Permission>("permission", 32)
    override val primaryKey = PrimaryKey(account, permission)
}

enum class Permission {
    VERIFIED,
    MANAGE_USERS,
    MANAGE_MAPS,
    SEE_USER_INFO,
}

enum class Achievement(val goal: Int = 1, val secret: Boolean = false) {
    ACTIVE(7, true),
    HYPER(30, true),
    ADDICT(90, true),
    GAMER(8 * 60),
    STEAM,
    DISCORD,
    DAY(24 * 60),
    WEEK(7 * 24 * 60),
    MONTH(30 * 24 * 60);

    data class Progression(var progress: Int = 0, var completed: Boolean = false)
}

object LegacyAccountTable : IntIdTable("account_legacy") {
    val usernameHash = binary("username_hash", 32).uniqueIndex()
    val passwordHash = binary("password_hash", 64)
    val passwordSalt = binary("password_salt", 64)
    val verified = bool("verified")
    val games = integer("games")
    val playtime = duration("playtime")
}

object LegacyAccountAchievementTable : Table("account_legacy_achievement") {
    val account =
        reference("legacy_account_id", LegacyAccountTable, onDelete = ReferenceOption.CASCADE)
    val achievement = enumerationByName<Achievement>("achievement", 32)
    override val primaryKey = PrimaryKey(account, achievement)
}

data class Account(
    val snowflake: Snowflake,
    val username: String,
    val discord: Long?,
    val games: Int,
    val playtime: Duration,
    val creation: Instant,
    val legacy: Boolean,
    val verified: Boolean
)
