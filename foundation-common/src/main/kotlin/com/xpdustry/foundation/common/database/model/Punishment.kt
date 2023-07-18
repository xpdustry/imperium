/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.common.database.model

import com.xpdustry.foundation.common.database.Entity
import com.xpdustry.foundation.common.database.timestamp
import org.bson.codecs.pojo.annotations.BsonIgnore
import org.bson.types.ObjectId
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

data class Punishment(
    override val id: ObjectId = ObjectId(),
    var targetIp: InetAddress,
    var targetUuid: MindustryUUID,
    var reason: String = "Unknown",
    var duration: Duration? = Duration.ofDays(1L),
    var pardonned: Boolean = false,
) : Entity<ObjectId> {
    @get:BsonIgnore
    val expired: Boolean
        get() = duration != null && (pardonned || timestamp.plus(duration).isBefore(Instant.now()))

    @get:BsonIgnore
    val remaining: Duration
        get() = if (duration == null) Duration.ZERO else duration!!.minus(Duration.between(Instant.now(), timestamp))
}
