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
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bson.BsonBinary
import org.bson.BsonDateTime
import org.bson.codecs.kotlinx.BsonDecoder
import org.bson.codecs.kotlinx.BsonEncoder
import org.bson.types.ObjectId

typealias SerializableInetAddress = @Serializable(with = InetAddressSerializer::class) InetAddress

object InetAddressSerializer : KSerializer<InetAddress> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InetAddress", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: InetAddress) =
        encoder.encodeString(value.hostAddress)

    override fun deserialize(decoder: Decoder): InetAddress =
        InetAddresses.forString(decoder.decodeString())
}

typealias SerializableUUID = @Serializable(with = UUIDSerializer::class) UUID

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

typealias SerializableObjectId = @Serializable(with = ObjectIdSerializer::class) ObjectId

object ObjectIdSerializer : KSerializer<ObjectId> {
    override val descriptor = PrimitiveSerialDescriptor("ObjectId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ObjectId) =
        when (encoder) {
            is BsonEncoder -> encoder.encodeObjectId(value)
            else -> encoder.encodeString(value.toString())
        }

    override fun deserialize(decoder: Decoder): ObjectId =
        when (decoder) {
            is BsonDecoder -> decoder.decodeObjectId()
            else -> ObjectId(decoder.decodeString())
        }
}

typealias SerializableJInstant = @Serializable(with = JavaInstantSerializer::class) Instant

object JavaInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("JavaInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) =
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonDateTime(value.toEpochMilli()))
            else -> encoder.encodeString(value.toString())
        }

    override fun deserialize(decoder: Decoder): Instant =
        when (decoder) {
            is BsonDecoder -> Instant.ofEpochMilli(decoder.decodeBsonValue().asDateTime().value)
            else -> Instant.parse(decoder.decodeString())
        }
}

typealias SerializableJDuration = @Serializable(with = JavaDurationSerializer::class) Duration

object JavaDurationSerializer : KSerializer<Duration> {
    override val descriptor = PrimitiveSerialDescriptor("JavaDuration", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Duration) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Duration = Duration.parse(decoder.decodeString())
}

typealias ByteArrayAsBsonBinary =
    @Serializable(with = ByteArrayAsBsonBinarySerializer::class) ByteArray

object ByteArrayAsBsonBinarySerializer : KSerializer<ByteArray> {
    override val descriptor =
        PrimitiveSerialDescriptor("ByteArrayAsBsonBinary", PrimitiveKind.STRING)
    private val serializer = ByteArraySerializer()

    override fun serialize(encoder: Encoder, value: ByteArray) =
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonBinary(value))
            else -> serializer.serialize(encoder, value)
        }

    override fun deserialize(decoder: Decoder): ByteArray =
        when (decoder) {
            is BsonDecoder -> decoder.decodeBsonValue().asBinary().data
            else -> serializer.deserialize(decoder)
        }
}
