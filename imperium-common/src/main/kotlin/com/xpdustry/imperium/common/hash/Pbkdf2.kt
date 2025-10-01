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

import com.password4j.HashBuilder
import com.password4j.PBKDF2Function
import com.password4j.Password
import com.password4j.SaltGenerator
import com.password4j.SecureString
import com.password4j.types.Hmac
import com.xpdustry.imperium.common.async.ImperiumScope
import java.util.Locale
import kotlinx.coroutines.withContext

data class PBKDF2Params(val hmac: Hmac, val iterations: Int, val length: Int, val saltLength: Int) : HashParams {
    init {
        require(iterations > 0) { "iterations must be positive" }
        require(length > 0) { "length must be positive" }
    }

    override fun toString() = "pbkdf2/h=${hmac.name.lowercase(Locale.ROOT)},i=$iterations,l=$length"

    enum class Hmac {
        SHA1,
        SHA224,
        SHA256,
        SHA384,
        SHA512,
    }
}

object PBKDF2HashFunction : SaltyHashFunction<PBKDF2Params> {

    override suspend fun create(chars: CharArray, params: PBKDF2Params): Hash =
        create0(Password.hash(SecureString(chars)), SaltGenerator.generate(params.saltLength), params)

    override suspend fun create(bytes: ByteArray, params: PBKDF2Params): Hash =
        create0(Password.hash(bytes), SaltGenerator.generate(params.saltLength), params)

    override suspend fun create(chars: CharArray, params: PBKDF2Params, salt: ByteArray): Hash =
        create0(Password.hash(SecureString(chars)), salt, params)

    override suspend fun create(bytes: ByteArray, params: PBKDF2Params, salt: ByteArray): Hash =
        create0(Password.hash(bytes), salt, params)

    private suspend fun create0(builder: HashBuilder, salt: ByteArray, params: PBKDF2Params): Hash =
        withContext(ImperiumScope.MAIN.coroutineContext) {
            val result =
                builder
                    .addSalt(salt)
                    .with(PBKDF2Function.getInstance(params.hmac.toP4J(), params.iterations, params.length))
            Hash(result.bytes, result.saltBytes, params)
        }

    private fun PBKDF2Params.Hmac.toP4J() =
        when (this) {
            PBKDF2Params.Hmac.SHA1 -> Hmac.SHA1
            PBKDF2Params.Hmac.SHA224 -> Hmac.SHA224
            PBKDF2Params.Hmac.SHA256 -> Hmac.SHA256
            PBKDF2Params.Hmac.SHA384 -> Hmac.SHA384
            PBKDF2Params.Hmac.SHA512 -> Hmac.SHA512
        }
}
