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

import com.password4j.HashBuilder
import com.password4j.PBKDF2Function
import com.password4j.Password
import com.password4j.SecureString
import com.password4j.types.Hmac
import java.util.Locale
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

data class PBKDF2Params(val hmac: Hmac, val iterations: Int, val length: Int) : HashParams {
    init {
        require(iterations > 0) { "iterations must be positive" }
        require(length > 0) { "length must be positive" }
    }

    override fun toString(): String {
        return "pbkdf2/h=${hmac.name.lowercase(Locale.ROOT)},i=$iterations,l=$length"
    }

    enum class Hmac {
        SHA1, SHA224, SHA256, SHA384, SHA512
    }

    companion object {
        fun fromString(str: String): PBKDF2Params {
            if (!str.startsWith("pbkdf2/")) {
                throw IllegalArgumentException("Invalid pbkdf2 params: $str")
            }
            val params = str.substring("pbkdf2/".length)
                .split(",")
                .map { it.trim().split("=") }
                .associate { it[0] to it[1] }
            return PBKDF2Params(
                Hmac.valueOf(params["h"]!!.uppercase(Locale.ROOT)),
                params["i"]!!.toInt(),
                params["l"]!!.toInt()
            )
        }
    }
}

object PBKDF2Hasher : HashFunction<PBKDF2Params> {

    override fun create(password: CharArray, params: PBKDF2Params, saltLength: Int): Mono<Hash> {
        return create0(Password.hash(SecureString(password)).addRandomSalt(saltLength), params)
    }

    override fun create(password: CharArray, params: PBKDF2Params, salt: ByteArray): Mono<Hash> {
        return create0(Password.hash(SecureString(password)).addSalt(salt), params)
    }

    private fun create0(builder: HashBuilder, params: PBKDF2Params): Mono<Hash> {
        return Mono.fromSupplier {
            builder.with(
                PBKDF2Function.getInstance(
                    params.hmac.toP4J(),
                    params.iterations,
                    params.length
                )
            )
        }
            .map { Hash(it.bytes, it.saltBytes) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    private fun PBKDF2Params.Hmac.toP4J() = when (this) {
        PBKDF2Params.Hmac.SHA1 -> Hmac.SHA1
        PBKDF2Params.Hmac.SHA224 -> Hmac.SHA224
        PBKDF2Params.Hmac.SHA256 -> Hmac.SHA256
        PBKDF2Params.Hmac.SHA384 -> Hmac.SHA384
        PBKDF2Params.Hmac.SHA512 -> Hmac.SHA512
    }
}
