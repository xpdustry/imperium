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

import com.xpdustry.imperium.common.misc.MindustryUUID
import java.net.InetAddress
import kotlinx.coroutines.flow.Flow
import org.bson.types.ObjectId

interface UserManager {
    suspend fun findById(id: ObjectId): User?

    suspend fun findByUuidOrCreate(uuid: MindustryUUID): User

    suspend fun findByUuid(uuid: MindustryUUID): User?

    suspend fun findByLastAddress(address: InetAddress): User?

    suspend fun searchUser(query: String): Flow<User>

    suspend fun updateOrCreateByUuid(uuid: MindustryUUID, updater: suspend (User) -> Unit)

    suspend fun getSetting(uuid: MindustryUUID, setting: User.Setting): Boolean =
        findByUuid(uuid)?.settings?.get(setting.name) ?: setting.default
}
