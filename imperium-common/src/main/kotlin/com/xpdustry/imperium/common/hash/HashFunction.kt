/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
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
package com.xpdustry.imperium.common.hash

interface HashFunction<P : HashParams> {
    suspend fun create(chars: CharArray, params: P): Hash

    suspend fun create(bytes: ByteArray, params: P): Hash
}

interface SaltyHashFunction<P : HashParams> : HashFunction<P> {
    suspend fun create(chars: CharArray, params: P, salt: ByteArray): Hash

    suspend fun create(bytes: ByteArray, params: P, salt: ByteArray): Hash
}

object GenericSaltyHashFunction : SaltyHashFunction<HashParams> {
    suspend fun equals(chars: CharArray, hash: Hash): Boolean = create(chars, hash.params, hash.salt) == hash

    override suspend fun create(chars: CharArray, params: HashParams): Hash =
        when (params) {
            is Argon2Params -> Argon2HashFunction.create(chars, params)
            is PBKDF2Params -> PBKDF2HashFunction.create(chars, params)
            else -> throw IllegalArgumentException("Unsupported params: $params")
        }

    override suspend fun create(bytes: ByteArray, params: HashParams): Hash =
        when (params) {
            is Argon2Params -> Argon2HashFunction.create(bytes, params)
            is PBKDF2Params -> PBKDF2HashFunction.create(bytes, params)
            else -> throw IllegalArgumentException("Unsupported params: $params")
        }

    override suspend fun create(chars: CharArray, params: HashParams, salt: ByteArray): Hash =
        when (params) {
            is Argon2Params -> Argon2HashFunction.create(chars, params, salt)
            is PBKDF2Params -> PBKDF2HashFunction.create(chars, params, salt)
            else -> throw IllegalArgumentException("Unsupported params: $params")
        }

    override suspend fun create(bytes: ByteArray, params: HashParams, salt: ByteArray): Hash =
        when (params) {
            is Argon2Params -> Argon2HashFunction.create(bytes, params, salt)
            is PBKDF2Params -> PBKDF2HashFunction.create(bytes, params, salt)
            else -> throw IllegalArgumentException("Unsupported params: $params")
        }
}
