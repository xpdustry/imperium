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

import com.password4j.Argon2Function
import com.password4j.HashBuilder
import com.password4j.Password
import com.password4j.SaltGenerator
import com.password4j.SecureString
import com.password4j.types.Argon2
import com.xpdustry.imperium.common.async.ImperiumScope
import java.util.Locale
import kotlinx.coroutines.withContext

data class Argon2Params(
    val memory: Int,
    val iterations: Int,
    val length: Int,
    val parallelism: Int,
    val type: Type,
    val version: Version,
    val saltLength: Int,
) : HashParams {
    init {
        require(memory > 0) { "memory must be positive" }
        require(iterations > 0) { "iterations must be positive" }
        require(length > 0) { "length must be positive" }
        require(parallelism > 0) { "parallelism must be positive" }
    }

    override fun toString() =
        "argon2/m=$memory,i=$iterations,p=$parallelism,l=$length,t=${type.name.lowercase(Locale.ROOT)},v=${version.name.lowercase()},s=$saltLength"

    enum class Type {
        ID,
        I,
        D,
    }

    enum class Version {
        V10,
        V13,
    }
}

object Argon2HashFunction : SaltyHashFunction<Argon2Params> {

    override suspend fun create(chars: CharArray, params: Argon2Params): Hash =
        create0(Password.hash(SecureString(chars)), SaltGenerator.generate(params.saltLength), params)

    override suspend fun create(bytes: ByteArray, params: Argon2Params): Hash =
        create0(Password.hash(bytes), SaltGenerator.generate(params.saltLength), params)

    override suspend fun create(chars: CharArray, params: Argon2Params, salt: ByteArray): Hash =
        create0(Password.hash(SecureString(chars)), salt, params)

    override suspend fun create(bytes: ByteArray, params: Argon2Params, salt: ByteArray): Hash =
        create0(Password.hash(bytes), salt, params)

    private suspend fun create0(builder: HashBuilder, salt: ByteArray, params: Argon2Params): Hash =
        withContext(ImperiumScope.MAIN.coroutineContext) {
            val result =
                builder
                    .addSalt(salt)
                    .with(
                        Argon2Function.getInstance(
                            params.memory,
                            params.iterations,
                            params.parallelism,
                            params.length,
                            params.type.toP4J(),
                            params.version.toP4J(),
                        )
                    )
            Hash(result.bytes, salt, params)
        }

    private fun Argon2Params.Type.toP4J() =
        when (this) {
            Argon2Params.Type.ID -> Argon2.ID
            Argon2Params.Type.I -> Argon2.I
            Argon2Params.Type.D -> Argon2.D
        }

    private fun Argon2Params.Version.toP4J() =
        when (this) {
            Argon2Params.Version.V10 -> Argon2Function.ARGON2_VERSION_10
            Argon2Params.Version.V13 -> Argon2Function.ARGON2_VERSION_13
        }
}
