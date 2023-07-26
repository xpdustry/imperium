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
package com.xpdustry.foundation.common.service

import com.google.common.annotations.VisibleForTesting
import com.xpdustry.foundation.common.database.Account
import com.xpdustry.foundation.common.database.Database
import com.xpdustry.foundation.common.database.LegacyAccount
import com.xpdustry.foundation.common.hash.Argon2HashFunction
import com.xpdustry.foundation.common.hash.Argon2Params
import com.xpdustry.foundation.common.hash.GenericSaltyHashFunction
import com.xpdustry.foundation.common.hash.PBKDF2Params
import com.xpdustry.foundation.common.hash.ShaHashFunction
import com.xpdustry.foundation.common.hash.ShaType
import com.xpdustry.foundation.common.misc.DEFAULT_PASSWORD_REQUIREMENTS
import com.xpdustry.foundation.common.misc.DEFAULT_USERNAME_REQUIREMENTS
import com.xpdustry.foundation.common.misc.RateLimiter
import com.xpdustry.foundation.common.misc.UsernameRequirement
import com.xpdustry.foundation.common.misc.findMissingPasswordRequirements
import com.xpdustry.foundation.common.misc.findMissingUsernameRequirements
import com.xpdustry.foundation.common.misc.switchIfEmpty
import com.xpdustry.foundation.common.misc.then
import com.xpdustry.foundation.common.misc.toBase64
import com.xpdustry.foundation.common.misc.toErrorMono
import com.xpdustry.foundation.common.misc.toValueMono
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.Base64

class SimpleAccountService(private val database: Database) : AccountService {

    private val limiter = RateLimiter<AccountRateLimitKey>(5, Duration.ofMinutes(5L))

    override fun register(username: String, password: CharArray, identity: PlayerIdentity, allowReservedUsernames: Boolean): Mono<Void> {
        val normalized = username.normalize()
        return checkRateLimit("register", identity)
            .then { database.accounts.findByUsername(normalized) }
            .flatMap<Void> {
                AccountException.AlreadyRegistered().toErrorMono()
            }
            .switchIfEmpty {
                findLegacyAccountByUsername(normalized).flatMap {
                    AccountException.InvalidUsername(listOf(UsernameRequirement.Reserved(normalized))).toErrorMono()
                }
            }
            .switchIfEmpty {
                val missingPwdRequirements = DEFAULT_PASSWORD_REQUIREMENTS.findMissingPasswordRequirements(password)
                if (missingPwdRequirements.isNotEmpty()) {
                    return@switchIfEmpty AccountException.InvalidPassword(missingPwdRequirements).toErrorMono()
                }
                val missingUsrRequirements = DEFAULT_USERNAME_REQUIREMENTS.findMissingUsernameRequirements(normalized)
                    .filter { allowReservedUsernames && it !is UsernameRequirement.Reserved }
                if (missingUsrRequirements.isNotEmpty()) {
                    return@switchIfEmpty AccountException.InvalidUsername(missingUsrRequirements).toErrorMono()
                }
                GenericSaltyHashFunction.create(password, PASSWORD_PARAMS).flatMap {
                    database.accounts.save(Account(normalized, it))
                }
            }
    }

    override fun migrate(oldUsername: String, newUsername: String, password: CharArray, identity: PlayerIdentity): Mono<Void> =
        checkRateLimit("migrate", identity)
            .then { findLegacyAccountByUsername(oldUsername.normalize()) }
            .filterWhen { GenericSaltyHashFunction.equals(password, it.password) }
            .switchIfEmpty { AccountException.WrongPassword().toErrorMono() }
            .flatMap { legacy ->
                val missing = DEFAULT_USERNAME_REQUIREMENTS.findMissingUsernameRequirements(newUsername.normalize())
                if (missing.isNotEmpty()) {
                    return@flatMap AccountException.InvalidUsername(missing).toErrorMono()
                }
                GenericSaltyHashFunction.create(password, PASSWORD_PARAMS).flatMap {
                    database.accounts.save(
                        Account(
                            username = newUsername.normalize(),
                            password = it,
                            playtime = legacy.playtime,
                            rank = legacy.rank,
                            games = legacy.games,
                        ),
                    )
                }
            }

    override fun login(username: String, password: CharArray, identity: PlayerIdentity): Mono<Void> =
        checkRateLimit("login", identity)
            .then { database.accounts.findByUsername(username.normalize()) }
            .switchIfEmpty { AccountException.NotRegistered().toErrorMono() }
            .filterWhen { GenericSaltyHashFunction.equals(password, it.password) }
            .switchIfEmpty { AccountException.WrongPassword().toErrorMono() }
            .flatMap { account ->
                createSessionToken(identity).flatMap { token ->
                    account.cleanSessions()
                    account.addSession(token)
                    database.accounts.save(account)
                }
            }

    override fun logout(identity: PlayerIdentity, all: Boolean): Mono<Boolean> =
        findAccountByIdentity0(identity)
            .flatMap {
                if (all) it.t1.sessions.clear() else it.t1.sessions.remove(it.t2)
                database.accounts.save(it.t1).thenReturn(true)
            }
            .switchIfEmpty { false.toValueMono() }

    override fun refresh(identity: PlayerIdentity): Mono<Void> =
        findAccountByIdentity0(identity).flatMap {
            it.t1.addSession(it.t2)
            database.accounts.save(it.t1)
        }

    override fun changePassword(oldPassword: CharArray, newPassword: CharArray, identity: PlayerIdentity): Mono<Void> =
        checkRateLimit("changePassword", identity)
            .then { findAccountByIdentity(identity) }
            .switchIfEmpty { AccountException.NotLogged().toErrorMono() }
            .filterWhen { GenericSaltyHashFunction.equals(oldPassword, it.password) }
            .switchIfEmpty { AccountException.WrongPassword().toErrorMono() }
            .flatMap { account ->
                val missing = DEFAULT_PASSWORD_REQUIREMENTS.findMissingPasswordRequirements(newPassword)
                if (missing.isNotEmpty()) {
                    return@flatMap AccountException.InvalidPassword(missing).toErrorMono()
                }
                GenericSaltyHashFunction.create(newPassword, PASSWORD_PARAMS).flatMap {
                    account.password = it
                    database.accounts.save(account)
                }
            }

    override fun findAccountByIdentity(identity: PlayerIdentity): Mono<Account> =
        findAccountByIdentity0(identity).map { it.t1 }

    private fun findAccountByIdentity0(identity: PlayerIdentity): Mono<Tuple2<Account, String>> =
        createSessionToken(identity).flatMap { token ->
            database.accounts.findBySessionToken(token)
                .filter { it.sessions[token]!!.expiration.isAfter(Instant.now()) }
                .zipWith(token.toValueMono())
        }

    private fun findLegacyAccountByUsername(username: String): Mono<LegacyAccount> =
        ShaHashFunction.create(username.toCharArray(), ShaType.SHA256)
            .flatMap { hash -> database.legacyAccounts.findById(hash.hash.toBase64()) }

    @VisibleForTesting
    internal fun createSessionToken(identity: PlayerIdentity): Mono<String> =
        Argon2HashFunction.create(identity.uuid.toCharArray(), SESSION_TOKEN_PARAMS, identity.usid.toCharArray())
            .map { Base64.getEncoder().encodeToString(it.hash) }

    private fun String.normalize(): String = trim().lowercase()

    private fun Account.cleanSessions() {
        val now = Instant.now()
        sessions.entries.removeIf { it.value.expiration.isBefore(now) }
    }

    private fun Account.addSession(token: String) {
        sessions[token] = Account.Session(Instant.now().plus(SESSION_TOKEN_DURATION))
    }

    private fun checkRateLimit(operation: String, identity: PlayerIdentity): Mono<Void> {
        if (limiter.checkAndIncrement(AccountRateLimitKey(operation, identity.address))) {
            return Mono.empty()
        }
        return AccountException.RateLimit().toErrorMono()
    }

    companion object {
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

        private val LEGACY_PASSWORD_PARAMS = PBKDF2Params(
            hmac = PBKDF2Params.Hmac.SHA256,
            iterations = 10000,
            length = 256,
            saltLength = 16,
        )
    }

    private data class AccountRateLimitKey(val operation: String, val address: InetAddress)
}
