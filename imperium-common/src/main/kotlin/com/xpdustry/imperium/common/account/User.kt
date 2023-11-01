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

import com.xpdustry.imperium.common.database.Entity
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.serialization.SerializableInetAddress
import com.xpdustry.imperium.common.serialization.SerializableJInstant
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    override val _id: MindustryUUID,
    val names: MutableSet<String> = mutableSetOf(),
    val addresses: MutableSet<SerializableInetAddress> = mutableSetOf(),
    var lastName: String? = null,
    var lastAddress: SerializableInetAddress? = null,
    var timesJoined: Int = 0,
    var firstJoin: SerializableJInstant = Instant.now(),
    var lastJoin: SerializableJInstant = Instant.now(),
) : Entity<MindustryUUID>
