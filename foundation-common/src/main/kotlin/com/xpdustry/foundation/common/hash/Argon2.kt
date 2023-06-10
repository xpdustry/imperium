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

import com.password4j.Argon2Function
import com.password4j.HashBuilder
import com.password4j.Password
import com.password4j.SecureString
import com.password4j.types.Argon2
import java.util.Locale
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

data class Argon2Params(
    val memory: Int,
    val iterations: Int,
    val length: Int,
    val parallelism: Int,
    val type: Type,
    val version: Version
) : HashParams {
    init {
        require(memory > 0) { "memory must be positive" }
        require(iterations > 0) { "iterations must be positive" }
        require(length > 0) { "length must be positive" }
        require(parallelism > 0) { "parallelism must be positive" }
    }

    override fun toString(): String {
        return "argon2/m=$memory,i=$iterations,p=$parallelism,l=$length,t=${type.name.lowercase(Locale.ROOT)},v=${
            version.name.lowercase(
                Locale.ROOT
            )
        }"
    }

    enum class Type {
        ID, I, D
    }

    enum class Version {
        V10, V13
    }

    companion object {
        fun fromString(str: String): Argon2Params {
            if (!str.startsWith("argon2/")) {
                throw IllegalArgumentException("Invalid argon2 params: $str")
            }
            val params = str.substring("argon2/".length)
                .split(",")
                .map { it.trim().split("=") }
                .associate { it[0] to it[1] }
            return Argon2Params(
                params["m"]!!.toInt(),
                params["i"]!!.toInt(),
                params["l"]!!.toInt(),
                params["p"]!!.toInt(),
                Type.valueOf(params["t"]!!.uppercase(Locale.ROOT)),
                Version.valueOf(params["v"]!!.uppercase(Locale.ROOT))
            )
        }
    }
}

object Argon2HashFunction : HashFunction<Argon2Params> {

    override fun create(password: CharArray, params: Argon2Params, salt: ByteArray): Mono<Hash> {
        return create0(Password.hash(SecureString(password)).addSalt(salt), params)
    }

    override fun create(password: CharArray, params: Argon2Params, saltLength: Int): Mono<Hash> {
        return create0(Password.hash(SecureString(password)).addRandomSalt(saltLength), params)
    }

    private fun create0(builder: HashBuilder, params: Argon2Params): Mono<Hash> {
        return Mono.fromSupplier {
            builder.with(
                Argon2Function.getInstance(
                    params.memory,
                    params.iterations,
                    params.parallelism,
                    params.length,
                    params.type.toP4J(),
                    params.version.toP4J()
                )
            )
        }
            .map { Hash(it.bytes, it.saltBytes) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    private fun Argon2Params.Type.toP4J() = when (this) {
        Argon2Params.Type.ID -> Argon2.ID
        Argon2Params.Type.I -> Argon2.I
        Argon2Params.Type.D -> Argon2.D
    }

    private fun Argon2Params.Version.toP4J() = when (this) {
        Argon2Params.Version.V10 -> Argon2Function.ARGON2_VERSION_10
        Argon2Params.Version.V13 -> Argon2Function.ARGON2_VERSION_13
    }
}
