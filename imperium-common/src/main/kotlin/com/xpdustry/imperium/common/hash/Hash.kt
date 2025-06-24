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

import com.xpdustry.imperium.common.misc.encodeBase64
import java.util.Objects

fun Hash(hash: ByteArray, salt: ByteArray, params: HashParams): Hash = Hash(params, hash.clone(), salt.clone())

class Hash internal constructor(val params: HashParams, private val _hash: ByteArray, private val _salt: ByteArray) {
    val hash: ByteArray
        get() = _hash.clone()

    val salt: ByteArray
        get() = _salt.clone()

    override fun equals(other: Any?): Boolean =
        other is Hash &&
            timeConstantEquals(hash, other.hash) &&
            timeConstantEquals(salt, other.salt) &&
            params == other.params

    override fun hashCode() = Objects.hash(_hash.contentHashCode(), _salt.contentHashCode(), params)

    override fun toString() = "Hash(hash=${_hash.encodeBase64()}, salt=${_salt.encodeBase64()}, params=$params)"
}

// Stolen from password4j
private fun timeConstantEquals(a: ByteArray, b: ByteArray): Boolean {
    var diff = a.size xor b.size
    var i = 0
    while (i < a.size && i < b.size) {
        diff = diff or (a[i].toInt() xor b[i].toInt())
        i++
    }
    return diff == 0
}
