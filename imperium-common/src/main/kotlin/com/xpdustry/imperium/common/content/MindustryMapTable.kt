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
package com.xpdustry.imperium.common.content

import com.xpdustry.imperium.common.user.UserTable
import java.time.Duration
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.duration
import org.jetbrains.exposed.sql.javatime.timestamp

object MindustryMapTable : IntIdTable("mindustry_map") {
    val name = varchar("name", 64)
    val description = text("description").nullable()
    val author = varchar("author", 64).nullable()
    val width = integer("width")
    val height = integer("height")
    val lastUpdate = timestamp("last_update").defaultExpression(CurrentTimestamp)
    val timestamp = timestamp("timestamp").defaultExpression(CurrentTimestamp)
}

object MindustryMapGameTable : IntIdTable("mindustry_map_game") {
    val map = reference("map_id", MindustryMapTable, onDelete = ReferenceOption.CASCADE)
    val server = varchar("server", 64)
    val start = timestamp("start").defaultExpression(CurrentTimestamp)
    val playtime = duration("playtime").default(Duration.ZERO)
    val unitsCreated = integer("units_created").default(0)
    val ennemiesKilled = integer("ennemies_killed").default(0)
    val wavesLasted = integer("waves_lasted").default(0)
    val buildingsConstructed = integer("buildings_constructed").default(0)
    val buildingsDeconstructed = integer("buildings_deconstructed").default(0)
    val buildingsDestroyed = integer("buildings_destroyed").default(0)
    val winner = ubyte("winner").default(UByte.MIN_VALUE)
    val timestamp = timestamp("timestamp").defaultExpression(CurrentTimestamp)
}

object MindustryMapGamemodeTable : Table("mindustry_map_gamemode") {
    val map = reference("map_id", MindustryMapTable, onDelete = ReferenceOption.CASCADE)
    val gamemode = enumerationByName<MindustryGamemode>("gamemode", 32)
    override val primaryKey = PrimaryKey(map, gamemode)
}

object MindustryMapRatingTable : Table("mindustry_map_rating") {
    val map = reference("map_id", MindustryMapTable, onDelete = ReferenceOption.CASCADE)
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val score = integer("score")
    val difficulty = enumerationByName<MindustryMap.Difficulty>("difficulty", 32)
    override val primaryKey = PrimaryKey(map, user)
}
