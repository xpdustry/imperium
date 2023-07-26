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
package com.xpdustry.imperium.common.database.mongo

import com.google.common.net.InetAddresses
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistry
import java.net.InetAddress

class InetAddressCodecProvider : CodecProvider {
    override fun <T : Any> get(clazz: Class<T>, registry: CodecRegistry): Codec<T>? {
        if (InetAddress::class.java.isAssignableFrom(clazz)) {
            @Suppress("UNCHECKED_CAST")
            return InetAddressCodec as Codec<T>
        }
        return null
    }
}

object InetAddressCodec : Codec<InetAddress> {
    override fun getEncoderClass(): Class<InetAddress> = InetAddress::class.java

    override fun encode(writer: BsonWriter, value: InetAddress, encoderContext: EncoderContext) =
        writer.writeString(value.hostAddress)

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): InetAddress =
        InetAddresses.forString(reader.readString())
}
