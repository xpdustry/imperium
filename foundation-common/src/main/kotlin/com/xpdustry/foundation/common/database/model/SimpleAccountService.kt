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

import com.google.common.annotations.VisibleForTesting
import com.google.inject.Inject
import com.xpdustry.foundation.common.database.Database
import com.xpdustry.foundation.common.hash.Argon2HashFunction
import com.xpdustry.foundation.common.hash.Argon2Params
import com.xpdustry.foundation.common.hash.GenericSaltyHashFunction
import com.xpdustry.foundation.common.hash.ShaHashFunction
import com.xpdustry.foundation.common.hash.ShaType
import com.xpdustry.foundation.common.misc.switchIfEmpty
import com.xpdustry.foundation.common.misc.toValueMono
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.Base64

class SimpleAccountService @Inject constructor(private val database: Database) : AccountService {

    override fun login(token: SessionToken, username: String, password: CharArray): Mono<LoginResult> =
        ShaHashFunction.create(username.lowercase().toCharArray(), ShaType.SHA256)
            .flatMap { database.accounts.findByHashedUsername(Base64.getEncoder().encodeToString(it.hash)) }
            .flatMap { login0(it, token, password) }
            .switchIfEmpty { LoginResult.NotRegistered.toValueMono() }

    override fun login(token: SessionToken, password: CharArray): Mono<LoginResult> =
        database.accounts.findByUuid(token.uuid)
            .flatMap { account -> login0(account, token, password) }
            .switchIfEmpty { LoginResult.NotRegistered.toValueMono() }

    private fun login0(account: Account, token: SessionToken, password: CharArray): Mono<LoginResult> =
        GenericSaltyHashFunction.create(password, account.password.params, account.password.salt)
            .filter { it == account.password }
            .flatMap<LoginResult> {
                updatePassword(account, password)
                    .then(updateSessions(token, account))
                    .then(database.accounts.save(account))
                    .thenReturn(LoginResult.Success)
            }
            .switchIfEmpty { LoginResult.WrongPassword.toValueMono() }

    override fun register(token: SessionToken, password: CharArray): Mono<RegisterResult> {
        return database.accounts.findByUuid(token.uuid)
            .map<RegisterResult> { RegisterResult.AlreadyRegistered }
            .switchIfEmpty { register0(token, password) }
    }

    private fun register0(token: SessionToken, password: CharArray): Mono<RegisterResult> {
        val check = checkPassword(password)
        if (check != null) return Mono.just(check)

        return GenericSaltyHashFunction.create(password, PASSWORD_PARAMS)
            .map { hash -> Account(uuids = mutableSetOf(token.uuid), password = hash) }
            .flatMap { account -> database.accounts.save(account) }
            .thenReturn(RegisterResult.Success)
    }

    @VisibleForTesting
    internal fun checkPassword(password: CharArray): RegisterResult.InvalidPassword? = when {
        password.size < PASSWORD_MIN_LENGTH || password.size > PASSWORD_MAX_LENGTH ->
            RegisterResult.InvalidPassword.Length(PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH)
        password.none { it.isLetter() } -> RegisterResult.InvalidPassword.NoLetter
        password.none { it.isDigit() } -> RegisterResult.InvalidPassword.NoNumber
        password.none { !it.isLetterOrDigit() } -> RegisterResult.InvalidPassword.NoSymbol
        else -> null
    }

    override fun logout(token: SessionToken): Mono<Void> = database.accounts
        .findByUuid(token.uuid)
        .flatMap { account ->
            hashSessionToken(token)
                .doOnNext { hash -> account.sessions.remove(hash) }
                .then(database.accounts.save(account))
        }

    override fun refresh(token: SessionToken): Mono<Void> =
        getAccount(token).flatMap { account -> updateSessions(token, account).then(database.accounts.save(account)) }

    override fun getAccount(token: SessionToken): Mono<Account> = database.accounts
        .findByUuid(token.uuid)
        .filterWhen { account ->
            hashSessionToken(token).map { hash -> account.sessions.contains(hash) }
        }

    private fun updateSessions(token: SessionToken, account: Account): Mono<Void> =
        hashSessionToken(token).doOnNext { hash ->
            val now = Instant.now()
            account.sessions.entries.removeIf { it.value.isBefore(now) }
            account.sessions[hash] = now.plus(SESSION_TOKEN_DURATION)
        }.then()

    private fun updatePassword(account: Account, password: CharArray): Mono<Void> {
        if (account.password.params == PASSWORD_PARAMS) {
            return Mono.empty()
        }
        return Argon2HashFunction.create(password, PASSWORD_PARAMS)
            .doOnNext { account.password = it }
            .then(database.accounts.save(account))
    }

    @VisibleForTesting
    internal fun hashSessionToken(token: SessionToken): Mono<HashedSessionToken> =
        Argon2HashFunction.create(token.uuid.toCharArray(), SESSION_TOKEN_PARAMS, token.usid.toCharArray())
            .map { Base64.getEncoder().encodeToString(it.hash) }

    companion object {
        internal const val PASSWORD_MIN_LENGTH = 8
        internal const val PASSWORD_MAX_LENGTH = 64

        private val SESSION_TOKEN_DURATION = Duration.ofDays(7L)

        private val SESSION_TOKEN_PARAMS = Argon2Params(
            memory = 19,
            iterations = 2,
            length = 32,
            saltLength = 8,
            parallelism = 8,
            type = Argon2Params.Type.ID,
            version = Argon2Params.Version.V13,
        )

        private val PASSWORD_PARAMS = Argon2Params(
            memory = 64 * 1024,
            iterations = 3,
            parallelism = 2,
            length = 64,
            type = Argon2Params.Type.ID,
            version = Argon2Params.Version.V13,
            saltLength = 64,
        )
    }
}
