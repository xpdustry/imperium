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
package com.xpdustry.imperium.common.database

import com.xpdustry.imperium.common.security.PasswordRequirement
import com.xpdustry.imperium.common.security.UsernameRequirement
import org.bson.types.ObjectId
import java.net.InetAddress

interface AccountManager {
    suspend fun findByIdentity(identity: PlayerIdentity): Account?
    suspend fun findByUsername(username: String): Account?
    suspend fun updateByIdentity(identity: PlayerIdentity, updater: suspend (Account) -> Unit)
    suspend fun updateById(id: ObjectId, updater: suspend (Account) -> Unit)
    suspend fun register(username: String, password: CharArray, identity: PlayerIdentity): AccountOperationResult
    suspend fun migrate(oldUsername: String, newUsername: String, password: CharArray, identity: PlayerIdentity): AccountOperationResult
    suspend fun login(username: String, password: CharArray, identity: PlayerIdentity): AccountOperationResult
    suspend fun logout(identity: PlayerIdentity, all: Boolean = false)
    suspend fun refresh(identity: PlayerIdentity)
    suspend fun changePassword(oldPassword: CharArray, newPassword: CharArray, identity: PlayerIdentity): AccountOperationResult
}

data class PlayerIdentity(val uuid: String, val usid: String, val address: InetAddress)

sealed interface AccountOperationResult {
    data object Success : AccountOperationResult
    data object AlreadyRegistered : AccountOperationResult
    data object NotRegistered : AccountOperationResult
    data object NotLogged : AccountOperationResult
    data object WrongPassword : AccountOperationResult
    data object RateLimit : AccountOperationResult
    data class InvalidPassword(val missing: List<PasswordRequirement>) : AccountOperationResult
    data class InvalidUsername(val missing: List<UsernameRequirement>) : AccountOperationResult
}
