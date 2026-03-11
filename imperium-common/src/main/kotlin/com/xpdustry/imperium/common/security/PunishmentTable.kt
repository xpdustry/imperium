// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.duration
import org.jetbrains.exposed.v1.javatime.timestamp

object PunishmentTable : IntIdTable("punishment") {
    val target = reference("target", UserTable, onDelete = ReferenceOption.CASCADE)
    val reason = varchar("reason", 256)
    val type = enumerationByName<Punishment.Type>("type", 32)
    val duration = duration("duration").nullable()
    val pardonTimestamp = timestamp("pardon_timestamp").nullable()
    val pardonReason = varchar("pardon_reason", 256).nullable()
    val server = varchar("server", 32)
    val creation = timestamp("creation").defaultExpression(CurrentTimestamp)
}
