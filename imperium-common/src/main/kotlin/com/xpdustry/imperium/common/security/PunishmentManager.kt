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
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.account.MindustryUUID
import com.xpdustry.imperium.common.database.snowflake.Snowflake
import java.net.InetAddress
import java.time.Duration
import kotlinx.coroutines.flow.Flow

interface PunishmentManager {
    suspend fun punish(
        author: Identity?,
        target: Punishment.Target,
        reason: String,
        type: Punishment.Type,
        duration: Duration?,
        extra: PunishmentMessage.Extra = PunishmentMessage.Extra.None
    )

    suspend fun pardon(author: Identity?, id: Snowflake, reason: String)

    suspend fun findById(id: Snowflake): Punishment?

    suspend fun findAllByAddress(target: InetAddress): Flow<Punishment>

    suspend fun findAllByUuid(uuid: MindustryUUID): Flow<Punishment>
}
