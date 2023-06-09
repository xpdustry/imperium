package com.xpdustry.foundation.common.database.mongo.codec

import java.time.Duration
import java.time.temporal.TemporalUnit
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class DurationCodec(private val precision: TemporalUnit) : Codec<Duration> {
    override fun getEncoderClass(): Class<Duration> = Duration::class.java

    override fun encode(writer: BsonWriter, value: Duration, encoderContext: EncoderContext) {
        writer.writeInt64(value.get(precision))
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Duration {
        return Duration.of(reader.readInt64(), precision)
    }
}
