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

import java.time.Duration
import java.time.Instant
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.BlobColumnType
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.duration
import org.jetbrains.exposed.sql.javatime.timestamp

object AccountTable : IntIdTable("account") {
    val username = varchar("username", 32).uniqueIndex()
    val password = binary("password", 64)
    val discord = long("discord").nullable().uniqueIndex().default(null)
    val games = integer("games").default(0)
    val playtime = duration("playtime").default(Duration.ZERO)
    val creation = timestamp("creation").clientDefault(Instant::now)
    val legacy = bool("legacy").default(false)
    val verified = bool("verified").default(false)
}

object AccountSessionTable : Table("account_user_session") {
    val account = reference("account", AccountTable, onDelete = ReferenceOption.CASCADE)
    val hash = binary("hash", 64)
    val expiration = timestamp("expiration")
    override val primaryKey = PrimaryKey(account, hash)
}

object AccountPermissionTable : Table("account_permission") {
    val account = reference("account", AccountTable, onDelete = ReferenceOption.CASCADE)
    val permission = enumerationByName<Permission>("permission", 32)
    override val primaryKey = PrimaryKey(account, permission)
}

object AccountAchievementTable : Table("account_achievement") {
    val account = reference("account", AccountTable, onDelete = ReferenceOption.CASCADE)
    val achievement = enumerationByName<AccountAchievement>("achievement", 32)
    val progress = integer("progress")
    val completed = bool("completed")
    override val primaryKey = PrimaryKey(account, achievement)
}

enum class Permission {
    VERIFIED,
    MANAGE_USERS,
    MANAGE_MAPS,
    SEE_USER_INFO,
}

enum class AccountAchievement {
    FIRST_LOGIN,
    FIRST_GAME,
    FIRST_WIN,
    FIRST_DEATH,
}

object LegacyAccountTable : Table("account_legacy") {
    val hashedUsername = varchar("hashed_username", 32).uniqueIndex()
    val password = binary("password", 64)
    val verified = bool("verified").default(false)
    val games = integer("games").default(0)
    val playtime = duration("playtime").default(Duration.ZERO)
    override val primaryKey = PrimaryKey(hashedUsername)
}

object LegacyAccountAchievementTable : Table("account_legacy_achievement") {
    val hashedUsername = reference("hashed_username", LegacyAccountTable.hashedUsername, onDelete = ReferenceOption.CASCADE)
    val achievement = enumerationByName<AccountAchievement>("achievement", 32)
    val progress = integer("progress")
    val completed = bool("completed")
    override val primaryKey = PrimaryKey(hashedUsername, achievement)
}