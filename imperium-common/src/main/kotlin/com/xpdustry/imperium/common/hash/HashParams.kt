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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = HashParamsSerializer::class)
sealed interface HashParams {
    companion object {
        fun fromString(str: String): HashParams =
            when {
                str.startsWith("argon2/") -> Argon2Params.fromString(str)
                str.startsWith("pbkdf2/") -> PBKDF2Params.fromString(str)
                str.startsWith("sha/") -> ShaType.fromString(str)
                else -> throw IllegalArgumentException("Unknown params: $str")
            }
    }
}

object HashParamsSerializer : KSerializer<HashParams> {
    override val descriptor = PrimitiveSerialDescriptor("HashParams", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: HashParams) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): HashParams =
        HashParams.fromString(decoder.decodeString())
}
