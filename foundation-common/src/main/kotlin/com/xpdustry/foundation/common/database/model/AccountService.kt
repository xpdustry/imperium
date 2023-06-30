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

import reactor.core.publisher.Mono

interface AccountService {
    fun register(token: SessionToken, password: CharArray): Mono<RegisterResult>
    fun login(token: SessionToken, username: String, password: CharArray): Mono<LoginResult>
    fun login(token: SessionToken, password: CharArray): Mono<LoginResult>
    fun logout(token: SessionToken): Mono<Void>
    fun refresh(token: SessionToken): Mono<Void>
    fun getAccount(token: SessionToken): Mono<Account>
}

data class SessionToken(val uuid: String, val usid: String)

sealed interface RegisterResult {
    object Success : RegisterResult
    object AlreadyRegistered : RegisterResult
    sealed interface InvalidPassword : RegisterResult {
        object NoLetter : InvalidPassword
        object NoNumber : InvalidPassword
        object NoSymbol : InvalidPassword
        data class Length(val min: Int, val max: Int) : InvalidPassword
    }
}

sealed interface LoginResult {
    object Success : LoginResult
    object WrongPassword : LoginResult
    object NotRegistered : LoginResult
}
