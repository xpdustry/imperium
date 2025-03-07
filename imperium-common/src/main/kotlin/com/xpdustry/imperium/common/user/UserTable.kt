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
package com.xpdustry.imperium.common.user

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object UserTable : IntIdTable("user") {
    val uuid = long("uuid").uniqueIndex()
    val lastName = varchar("last_name", 64)
    val lastAddress = binary("last_address", 16)
    val timesJoined = integer("times_joined").default(0)
    val lastJoin = timestamp("last_join").defaultExpression(CurrentTimestamp)
    val firstJoin = timestamp("first_join").defaultExpression(CurrentTimestamp)
}

object UserNameTable : Table("user_name") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 64)
    override val primaryKey = PrimaryKey(user, name)
}

object UserAddressTable : Table("user_address") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val address = binary("address", 16)
    override val primaryKey = PrimaryKey(user, address)
}

object UserSettingTable : Table("user_setting") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val setting = enumerationByName<Setting>("setting", 64)
    val value = bool("value")
    override val primaryKey = PrimaryKey(user, setting)
}
