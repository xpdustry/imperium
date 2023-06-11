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
package com.xpdustry.foundation.common.hash

import reactor.core.publisher.Mono

// NOTE: Wrapping hash in monos since computing can take a few seconds
interface HashFunction<P : HashParams> {
    fun create(password: CharArray, params: P, saltLength: Int): Mono<Hash>
    fun create(password: CharArray, params: P, salt: ByteArray): Mono<Hash>
}

object GenericHashFunction : HashFunction<HashParams> {
    override fun create(password: CharArray, params: HashParams, saltLength: Int): Mono<Hash> = when (params) {
        is Argon2Params -> Argon2HashFunction.create(password, params, saltLength)
        is PBKDF2Params -> PBKDF2Hasher.create(password, params, saltLength)
        else -> throw IllegalArgumentException("Unsupported params: $params")
    }

    override fun create(password: CharArray, params: HashParams, salt: ByteArray): Mono<Hash> = when (params) {
        is Argon2Params -> Argon2HashFunction.create(password, params, salt)
        is PBKDF2Params -> PBKDF2Hasher.create(password, params, salt)
        else -> throw IllegalArgumentException("Unsupported params: $params")
    }
}
