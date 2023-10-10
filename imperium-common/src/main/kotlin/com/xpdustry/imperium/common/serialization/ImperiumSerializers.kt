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
package com.xpdustry.imperium.common.serialization

import com.google.common.net.InetAddresses
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bson.types.ObjectId
import java.net.InetAddress
import java.util.UUID

object InetAddressSerializer : KSerializer<InetAddress> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InetAddress", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: InetAddress) = encoder.encodeString(value.hostAddress)
    override fun deserialize(decoder: Decoder): InetAddress = InetAddresses.forString(decoder.decodeString())
}

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

object ObjectIdSerializer : KSerializer<ObjectId> {
    override val descriptor = PrimitiveSerialDescriptor("ObjectId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ObjectId) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): ObjectId = ObjectId(decoder.decodeString())
}
