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

import com.xpdustry.foundation.common.misc.toErrorMono
import reactor.core.publisher.Mono

interface HashFunction<P : HashParams> {
    fun create(chars: CharArray, params: P): Mono<Hash>
    fun create(bytes: ByteArray, params: P): Mono<Hash>
}

interface SaltyHashFunction<P : HashParams> : HashFunction<P> {
    fun create(chars: CharArray, params: P, salt: CharArray): Mono<Hash>
    fun create(chars: CharArray, params: P, salt: ByteArray): Mono<Hash>
    fun create(bytes: ByteArray, params: P, salt: ByteArray): Mono<Hash>
}

object GenericSaltyHashFunction : SaltyHashFunction<HashParams> {
    override fun create(chars: CharArray, params: HashParams): Mono<Hash> = when (params) {
        is Argon2Params -> Argon2HashFunction.create(chars, params)
        is PBKDF2Params -> PBKDF2HashFunction.create(chars, params)
        else -> IllegalArgumentException("Unsupported params: $params").toErrorMono()
    }

    override fun create(bytes: ByteArray, params: HashParams): Mono<Hash> = when (params) {
        is Argon2Params -> Argon2HashFunction.create(bytes, params)
        is PBKDF2Params -> PBKDF2HashFunction.create(bytes, params)
        else -> IllegalArgumentException("Unsupported params: $params").toErrorMono()
    }

    override fun create(chars: CharArray, params: HashParams, salt: CharArray): Mono<Hash> = when (params) {
        is Argon2Params -> Argon2HashFunction.create(chars, params, salt)
        is PBKDF2Params -> PBKDF2HashFunction.create(chars, params, salt)
        else -> IllegalArgumentException("Unsupported params: $params").toErrorMono()
    }

    override fun create(chars: CharArray, params: HashParams, salt: ByteArray): Mono<Hash> = when (params) {
        is Argon2Params -> Argon2HashFunction.create(chars, params, salt)
        is PBKDF2Params -> PBKDF2HashFunction.create(chars, params, salt)
        else -> IllegalArgumentException("Unsupported params: $params").toErrorMono()
    }

    override fun create(bytes: ByteArray, params: HashParams, salt: ByteArray): Mono<Hash> = when (params) {
        is Argon2Params -> Argon2HashFunction.create(bytes, params, salt)
        is PBKDF2Params -> PBKDF2HashFunction.create(bytes, params, salt)
        else -> IllegalArgumentException("Unsupported params: $params").toErrorMono()
    }
}
