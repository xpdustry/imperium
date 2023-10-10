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
@file:UseSerializers(JavaDurationSerializer::class)

package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.database.Entity
import com.xpdustry.imperium.common.hash.Hash
import com.xpdustry.imperium.common.serialization.JavaDurationSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Duration

typealias HashedUsername = String

@Serializable
data class LegacyAccount(
    override val _id: HashedUsername,
    var password: Hash,
    var rank: Account.Rank = Account.Rank.NORMAL,
    var games: Int = 0,
    var playtime: Duration = Duration.ZERO,
    val achievements: MutableSet<Achievement> = mutableSetOf(),
) : Entity<HashedUsername>
