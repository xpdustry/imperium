// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.user

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

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
    val setting = enumerationByName<User.Setting>("setting", 64)
    val value = bool("value")
    override val primaryKey = PrimaryKey(user, setting)
}
