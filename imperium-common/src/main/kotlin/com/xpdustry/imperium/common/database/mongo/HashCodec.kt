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

import com.xpdustry.imperium.common.hash.Hash
import com.xpdustry.imperium.common.hash.HashParams
import org.bson.BsonBinary
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

internal class HashCodec : Codec<Hash> {
    override fun getEncoderClass(): Class<Hash> = Hash::class.java

    override fun encode(writer: BsonWriter, value: Hash, encoderContext: EncoderContext) {
        writer.writeStartDocument()
        writer.writeBinaryData("hash", BsonBinary(value.hash))
        writer.writeBinaryData("salt", BsonBinary(value.salt))
        writer.writeString("params", value.params.toString())
        writer.writeEndDocument()
    }

    // TODO: Handle unordered fields (Use BsonDocumentCodec or smt)
    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Hash {
        reader.readStartDocument()
        val hash = reader.readBinaryData("hash").data
        val salt = reader.readBinaryData("salt").data
        val params = HashParams.fromString(reader.readString("params"))
        reader.readEndDocument()
        return Hash(hash, salt, params)
    }
}
