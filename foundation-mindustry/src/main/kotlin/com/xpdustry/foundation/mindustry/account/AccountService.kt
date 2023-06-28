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
package com.xpdustry.foundation.mindustry.account

import com.xpdustry.foundation.common.database.Database
import com.xpdustry.foundation.common.database.model.Account
import com.xpdustry.foundation.common.hash.Argon2HashFunction
import com.xpdustry.foundation.common.hash.Argon2Params
import com.xpdustry.foundation.common.hash.DEFAULT_PARAMS
import com.xpdustry.foundation.common.hash.GenericSaltyHashFunction
import com.xpdustry.foundation.common.hash.ShaHashFunction
import com.xpdustry.foundation.common.hash.ShaType
import com.xpdustry.foundation.common.misc.switchIfEmpty
import com.xpdustry.foundation.common.misc.toValueMono
import fr.xpdustry.distributor.api.util.MUUID
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.Base64

val SESSION_DURATION: Duration = Duration.ofDays(7)

interface AccountService {
    fun register(muuid: MUUID, password: CharArray): Mono<RegisterResult>
    fun login(muuid: MUUID, username: String, password: CharArray): Mono<LoginStatus>
    fun login(muuid: MUUID, password: CharArray): Mono<LoginStatus>
    fun logout(muuid: MUUID): Mono<Void>
    fun refresh(muuid: MUUID): Mono<Void>
    fun getAccount(muuid: MUUID): Mono<Account>
}

sealed class RegisterResult {
    object Success : RegisterResult()
    object AlreadyRegistered : RegisterResult()
    data class InvalidPassword(val reason: Reason) : RegisterResult() {
        enum class Reason {
            NO_LETTER,
            NO_NUMBER,
            NO_SYMBOL,
            TOO_SHORT,
            TOO_LONG,
        }
    }
}

sealed class LoginStatus {
    object Success : LoginStatus()
    object WrongPassword : LoginStatus()
    object NotRegistered : LoginStatus()
}

// TODO: Cleanup ?
class SimpleAccountService @Inject constructor(private val database: Database) : AccountService {

    override fun login(muuid: MUUID, username: String, password: CharArray): Mono<LoginStatus> =
        ShaHashFunction.create(username.lowercase().toCharArray(), ShaType.SHA256)
            .flatMap { database.accounts.findByHashedUsername(Base64.getEncoder().encodeToString(it.hash)) }
            .flatMap { login0(it, muuid, password) }
            .switchIfEmpty { LoginStatus.NotRegistered.toValueMono() }

    override fun login(muuid: MUUID, password: CharArray): Mono<LoginStatus> =
        database.accounts.findByUuid(muuid.uuid)
            .flatMap { account -> login0(account, muuid, password) }
            .switchIfEmpty { LoginStatus.NotRegistered.toValueMono() }

    private fun login0(account: Account, muuid: MUUID, password: CharArray): Mono<LoginStatus> =
        GenericSaltyHashFunction.create(password, account.password.params, account.password.salt)
            .filter { it == account.password }
            .flatMap<LoginStatus> {
                Mono.empty<Void>()
                    .then(updatePassword(account, password))
                    .then(updateSessions(muuid, account))
                    .then(database.accounts.save(account))
                    .thenReturn(LoginStatus.Success)
            }
            .switchIfEmpty { LoginStatus.WrongPassword.toValueMono() }

    override fun register(muuid: MUUID, password: CharArray): Mono<RegisterResult> {
        return database.accounts.findByUuid(muuid.uuid)
            .map<RegisterResult> { RegisterResult.AlreadyRegistered }
            .switchIfEmpty { register0(muuid, password) }
    }

    private fun register0(muuid: MUUID, password: CharArray): Mono<RegisterResult> {
        val invalid = when {
            password.size < 8 -> RegisterResult.InvalidPassword(RegisterResult.InvalidPassword.Reason.TOO_SHORT)
            password.size > 64 -> RegisterResult.InvalidPassword(RegisterResult.InvalidPassword.Reason.TOO_LONG)
            password.none { it.isLetter() } -> RegisterResult.InvalidPassword(RegisterResult.InvalidPassword.Reason.NO_LETTER)
            password.none { it.isDigit() } -> RegisterResult.InvalidPassword(RegisterResult.InvalidPassword.Reason.NO_NUMBER)
            password.none { !it.isLetterOrDigit() } -> RegisterResult.InvalidPassword(RegisterResult.InvalidPassword.Reason.NO_SYMBOL)
            else -> null
        }

        if (invalid != null) return invalid.toValueMono()

        return GenericSaltyHashFunction.create(password, DEFAULT_PARAMS)
            .map { hash -> Account(uuids = mutableSetOf(muuid.uuid), password = hash) }
            .flatMap { account ->
                Mono.empty<Void>()
                    .then(updateSessions(muuid, account))
                    .then(database.accounts.save(account))
                    .thenReturn(RegisterResult.Success)
            }
    }

    override fun logout(muuid: MUUID): Mono<Void> = database.accounts
        .findByUuid(muuid.uuid)
        .flatMap { account ->
            createSessionToken(muuid)
                .doOnNext { token -> account.sessions.remove(token) }
                .then(database.accounts.save(account))
        }

    override fun refresh(muuid: MUUID): Mono<Void> =
        getAccount(muuid).flatMap { updateSessions(muuid, it).then(database.accounts.save(it)) }

    override fun getAccount(muuid: MUUID): Mono<Account> = database.accounts
        .findByUuid(muuid.uuid)
        .filterWhen { account ->
            createSessionToken(muuid).map { token -> account.sessions.contains(token) }
        }

    private fun updateSessions(muuid: MUUID, account: Account): Mono<Void> =
        createSessionToken(muuid)
            .doOnNext { token ->
                val now = Instant.now()
                account.sessions.entries.removeIf { it.value.isBefore(now) }
                account.sessions[token] = now.plus(SESSION_DURATION)
            }
            .then()

    private fun updatePassword(account: Account, password: CharArray): Mono<Void> {
        if (account.password.params == DEFAULT_PARAMS) {
            return Mono.empty()
        }
        return GenericSaltyHashFunction.create(password, DEFAULT_PARAMS)
            .doOnNext { account.password = it }
            .then(database.accounts.save(account))
    }

    private fun createSessionToken(muuid: MUUID): Mono<String> =
        Argon2HashFunction.create(muuid.uuid.toCharArray(), SESSION_TOKEN_PARAMS, muuid.usid.toCharArray())
            .map { Base64.getEncoder().encodeToString(it.hash) }

    companion object {
        private val SESSION_TOKEN_PARAMS = Argon2Params(
            memory = 19,
            iterations = 2,
            length = 32,
            saltLength = 8,
            parallelism = 8,
            type = Argon2Params.Type.ID,
            version = Argon2Params.Version.V13,
        )
    }
}
