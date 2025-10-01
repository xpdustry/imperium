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

import com.password4j.MessageDigestFunction
import com.password4j.SecureString
import com.xpdustry.imperium.common.async.ImperiumScope
import kotlinx.coroutines.withContext

enum class ShaType(val length: Int) : HashParams {
    SHA256(256);

    override fun toString() = "sha/$length"
}

object ShaHashFunction : HashFunction<ShaType> {

    override suspend fun create(bytes: ByteArray, params: ShaType): Hash =
        withContext(ImperiumScope.MAIN.coroutineContext) {
            Hash(getHashFunction(params).hash(bytes).bytes, ByteArray(0), params)
        }

    override suspend fun create(chars: CharArray, params: ShaType): Hash =
        withContext(ImperiumScope.MAIN.coroutineContext) {
            Hash(getHashFunction(params).hash(SecureString(chars)).bytes, ByteArray(0), params)
        }

    private fun getHashFunction(params: ShaType) =
        when (params) {
            ShaType.SHA256 -> MessageDigestFunction.getInstance("SHA-256")
        }
}
