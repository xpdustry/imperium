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

import com.xpdustry.foundation.common.misc.toBase64
import java.util.Objects

class Hash(hash: ByteArray, salt: ByteArray, val params: HashParams) {
    private val _hash: ByteArray = hash.clone()
    private val _salt: ByteArray = salt.clone()

    val hash: ByteArray get() = _hash.clone()
    val salt: ByteArray get() = _salt.clone()

    override fun equals(other: Any?): Boolean =
        other is Hash && timeConstantEquals(hash, other.hash) && timeConstantEquals(salt, other.salt) && params == other.params

    override fun hashCode() =
        Objects.hash(_hash.contentHashCode(), _salt.contentHashCode(), params)

    override fun toString() =
        "Password(hash=${_hash.toBase64()}, salt=${_salt.toBase64()}, params=$params)"
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
