/*
 * Imperium, the software collection powering the Xpdustry network.
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
package com.xpdustry.imperium.common.hash

import com.google.common.hash.Hashing
import com.password4j.SecureString
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

enum class ShaType(val length: Int) : HashParams {
    SHA256(256),
    ;

    override fun toString(): String {
        return "sha/$length"
    }

    companion object {
        fun fromString(str: String): ShaType {
            if (!str.startsWith("sha/")) {
                throw IllegalArgumentException("Invalid sha params: $str")
            }

            val length = str.substring("sha/".length).toInt()
            return values().find { it.length == length }
                ?: throw IllegalArgumentException("Unknown sha length: $length")
        }
    }
}

object ShaHashFunction : HashFunction<ShaType> {

    override fun create(bytes: ByteArray, params: ShaType): Mono<Hash> {
        return Mono.fromCallable {
            Hash(getHashFunction(params).hashBytes(bytes).asBytes(), ByteArray(0), params)
        }
            .subscribeOn(Schedulers.boundedElastic())
    }

    override fun create(chars: CharArray, params: ShaType): Mono<Hash> {
        return Mono.fromCallable {
            Hash(getHashFunction(params).hashString(SecureString(chars), Charsets.UTF_8).asBytes(), ByteArray(0), params)
        }
            .subscribeOn(Schedulers.boundedElastic())
    }

    private fun getHashFunction(params: ShaType) = when (params) {
        ShaType.SHA256 -> Hashing.sha256()
    }
}
