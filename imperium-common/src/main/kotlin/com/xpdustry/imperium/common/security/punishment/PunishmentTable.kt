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
package com.xpdustry.imperium.common.security.punishment

import com.xpdustry.imperium.common.snowflake.SnowflakeIdTable
import com.xpdustry.imperium.common.user.UserTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.duration
import org.jetbrains.exposed.sql.javatime.timestamp

object PunishmentTable : SnowflakeIdTable("punishment") {
    val authorId = reference("author_id", UserTable, onDelete = ReferenceOption.SET_NULL)
    val authorType = enumerationByName<Punishment.Author.Type>("author_type", 32)
    val targetAddress = binary("target_address", 16)
    val targetAddressMask = byte("target_address_mask")
    val targetUuid = binary("target_uuid", 16).nullable()
    val reason = varchar("reason", 256)
    val type = enumerationByName<Punishment.Type>("type", 32)
    val duration = duration("duration").nullable()
    val pardonTimestamp = timestamp("pardon_timestamp").nullable()
    val pardonReason = varchar("pardon_reason", 256).nullable()
    val pardonAuthorId = long("pardon_author_id").nullable()
    val pardonAuthorType =
        enumerationByName<Punishment.Author.Type>("pardon_author_type", 32).nullable()
}
