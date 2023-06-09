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
import java.net.InetAddress
import java.time.Duration

typealias MindustryUUID = String

class User(
    override val id: MindustryUUID,
    var names: Set<String> = emptySet(),
    var addresses: Set<InetAddress> = emptySet(),
    var lastName: String? = null,
    var lastAddress: InetAddress? = null,
    var timesJoined: Int = 0,
    var timesKicked: Int = 0,
    var gamesPlayed: Int = 0,
    var playTime: Duration = Duration.ZERO,
) : Entity<MindustryUUID>
