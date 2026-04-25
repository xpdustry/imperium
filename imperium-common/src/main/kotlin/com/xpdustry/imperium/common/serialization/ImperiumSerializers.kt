// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.serialization

import com.google.common.net.InetAddresses
import java.awt.Polygon
import java.net.InetAddress
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

typealias SerializableInetAddress = @Serializable(with = InetAddressSerializer::class) InetAddress

object InetAddressSerializer : KSerializer<InetAddress> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InetAddress", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: InetAddress) = encoder.encodeString(value.hostAddress)

    override fun deserialize(decoder: Decoder): InetAddress = InetAddresses.forString(decoder.decodeString())
}

typealias SerializableInstant = @Serializable(with = InstantSerializer::class) Instant

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

typealias SerializablePolygon = @Serializable(with = PolygonSerializer::class) Polygon

@OptIn(ExperimentalSerializationApi::class)
object PolygonSerializer : KSerializer<Polygon> {
    private val backing = ArraySerializer(IntArraySerializer())
    override val descriptor = SerialDescriptor("Polygon", backing.descriptor)

    override fun serialize(encoder: Encoder, value: Polygon) {
        val array = Array(value.npoints) { intArrayOf(value.xpoints[it], value.ypoints[it]) }
        encoder.encodeSerializableValue(backing, array)
    }

    override fun deserialize(decoder: Decoder): Polygon {
        val points = decoder.decodeSerializableValue(backing)
        val polygon = Polygon()
        for (point in points) {
            polygon.addPoint(point[0], point[1])
        }
        return polygon
    }
}
