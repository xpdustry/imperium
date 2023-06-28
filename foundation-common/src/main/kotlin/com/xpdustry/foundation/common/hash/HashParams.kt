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

val DEFAULT_PARAMS: HashParams = Argon2Params(
    memory = 64 * 1024,
    iterations = 3,
    parallelism = 2,
    length = 64,
    type = Argon2Params.Type.ID,
    version = Argon2Params.Version.V13,
    saltLength = 64,
)

interface HashParams {
    companion object {
        fun fromString(str: String): HashParams = when {
            str.startsWith("argon2/") -> Argon2Params.fromString(str)
            str.startsWith("pbkdf2/") -> PBKDF2Params.fromString(str)
            else -> throw IllegalArgumentException("Unknown params: $str")
        }
    }
}
