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
package com.xpdustry.imperium.mindustry.misc

import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.snowflake.Snowflake
import java.io.DataInput
import java.io.DataOutput
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import mindustry.Vars
import mindustry.io.SaveFileReader.CustomChunk
import mindustry.maps.Map

private const val MAP_SNOWFLAKE = "imperium-map-snowflake"
var Map.snowflake: Snowflake?
    get() = tags.get(MAP_SNOWFLAKE)?.toLongOrNull()
    set(value) {
        tags.put(MAP_SNOWFLAKE, value.toString())
    }

private const val MAP_START = "imperium-map-start"
var Map.start: Instant?
    get() = tags.get(MAP_START)?.toLongOrNull()?.let(Instant::ofEpochMilli)
    set(value) {
        tags.put(MAP_START, value?.toEpochMilli().toString())
    }

private const val MAP_PLAYTIME = "imperium-map-playtime"
var Map.playtime: Duration
    get() = tags.get(MAP_PLAYTIME)?.toLongOrNull()?.seconds ?: ZERO
    set(value) {
        tags.put(MAP_PLAYTIME, value.inWholeSeconds.toString())
    }

object ImperiumMetadataChunkReader : CustomChunk {

    private val logger by LoggerDelegate()

    override fun write(stream: DataOutput) {
        val json = buildJsonObject {
            Vars.state.map.snowflake?.let { put(MAP_SNOWFLAKE, it) }
            Vars.state.map.start?.toEpochMilli()?.let { put(MAP_START, it) }
            put(MAP_PLAYTIME, Vars.state.map.playtime.inWholeSeconds.toString())
        }
        stream.writeUTF(json.toString())
        logger.debug("Written imperium metadata: {}", json.toString())
    }

    override fun read(stream: DataInput) {
        val json = Json.parseToJsonElement(stream.readUTF()).jsonObject
        Vars.state.map.snowflake = json[MAP_SNOWFLAKE]?.jsonPrimitive?.long
        Vars.state.map.start = json[MAP_START]?.jsonPrimitive?.long?.let(Instant::ofEpochMilli)
        Vars.state.map.playtime = json[MAP_PLAYTIME]?.jsonPrimitive?.long?.seconds ?: ZERO
        logger.debug("Read imperium metadata: {}", json.toString())
    }
}
