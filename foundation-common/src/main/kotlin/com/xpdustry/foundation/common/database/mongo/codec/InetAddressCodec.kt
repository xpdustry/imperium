package com.xpdustry.foundation.common.database.mongo.codec

import com.google.common.net.InetAddresses
import java.net.InetAddress
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

object InetAddressCodec : Codec<InetAddress> {
    override fun getEncoderClass(): Class<InetAddress> = InetAddress::class.java

    override fun encode(writer: BsonWriter, value: InetAddress, encoderContext: EncoderContext) =
        writer.writeString(value.hostAddress)

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): InetAddress =
        InetAddresses.forString(reader.readString())
}