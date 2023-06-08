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
package com.xpdustry.foundation.common.database.mongo

import com.google.common.net.InetAddresses
import com.mongodb.MongoClientSettings
import com.xpdustry.foundation.common.database.Entity
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.pojo.ClassModelBuilder
import org.bson.codecs.pojo.Convention
import org.bson.codecs.pojo.Conventions
import org.bson.codecs.pojo.PojoCodecProvider
import java.net.InetAddress
import java.time.Duration
import java.time.temporal.TemporalUnit

object InetAddressCodec : Codec<InetAddress> {
    override fun getEncoderClass(): Class<InetAddress> = InetAddress::class.java

    override fun encode(writer: BsonWriter, value: InetAddress, encoderContext: EncoderContext) =
        writer.writeString(value.hostAddress)

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): InetAddress =
        InetAddresses.forString(reader.readString())
}

class DurationCodec(private val precision: TemporalUnit) : Codec<Duration> {
    override fun getEncoderClass(): Class<Duration> = Duration::class.java

    override fun encode(writer: BsonWriter, value: Duration, encoderContext: EncoderContext) {
        writer.writeInt64(value.get(precision))
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Duration {
        return Duration.of(reader.readInt64(), precision)
    }
}

object SnakeCaseConvention : Convention {
    override fun apply(classModelBuilder: ClassModelBuilder<*>) {
        classModelBuilder.propertyModelBuilders.forEach {
            it.readName(it.readName.camelToSnakeCase())
            it.writeName(it.writeName.camelToSnakeCase())
        }
    }
}

inline fun <reified E : Entity<*>> pojoCodecRegistry(codecs: List<Codec<*>> = emptyList()) = CodecRegistries.fromProviders(
    MongoClientSettings.getDefaultCodecRegistry(),
    PojoCodecProvider.builder()
        .register(E::class.java)
        .conventions(Conventions.DEFAULT_CONVENTIONS + listOf(SnakeCaseConvention))
        .build(),
    CodecRegistries.fromCodecs(codecs),
)

// https://www.baeldung.com/kotlin/convert-camel-case-snake-case
private fun String.camelToSnakeCase(): String {
    return this.fold(StringBuilder()) { acc, c ->
        acc.append(if (acc.isNotEmpty() && c.isUpperCase()) "_${c.lowercase()}" else c.lowercase())
    }.toString()
}
