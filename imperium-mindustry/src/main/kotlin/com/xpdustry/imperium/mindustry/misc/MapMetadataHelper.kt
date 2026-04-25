// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.misc

import com.xpdustry.imperium.common.misc.LoggerDelegate
import java.io.DataInput
import java.io.DataOutput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import mindustry.Vars
import mindustry.io.SaveFileReader.CustomChunk
import mindustry.maps.Map

// TODO Use a key container for this shit
private const val MAP_IDENTIFIER = "imperium-map-identifier"
var Map.id: Int?
    get() = tags.get(MAP_IDENTIFIER)?.toIntOrNull()
    set(value) {
        tags.put(MAP_IDENTIFIER, value.toString())
    }

private const val MAP_START = "imperium-map-start"
var Map.start: Instant?
    get() = tags.get(MAP_START)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
    set(value) {
        tags.put(MAP_START, value?.toEpochMilliseconds().toString())
    }

private const val MAP_PLAYTIME = "imperium-map-playtime"
var Map.playtime: Duration
    get() = tags.get(MAP_PLAYTIME)?.toLongOrNull()?.seconds ?: ZERO
    set(value) {
        tags.put(MAP_PLAYTIME, value.inWholeSeconds.toString())
    }

private const val MAP_DAY_NIGHT_CYCLE = "imperium-map-day-night-cicle"
var Map.dayNightCycle: Boolean
    get() = tags.get(MAP_DAY_NIGHT_CYCLE)?.toBooleanStrictOrNull() ?: false
    set(value) {
        tags.put(MAP_DAY_NIGHT_CYCLE, value.toString())
    }

object ImperiumMetadataChunkReader : CustomChunk {

    private val logger by LoggerDelegate()

    override fun write(stream: DataOutput) {
        val json = buildJsonObject {
            Vars.state.map.id?.let { put(MAP_IDENTIFIER, it) }
            Vars.state.map.start?.toEpochMilliseconds()?.let { put(MAP_START, it) }
            put(MAP_PLAYTIME, Vars.state.map.playtime.inWholeSeconds.toString())
            put(MAP_DAY_NIGHT_CYCLE, Vars.state.map.dayNightCycle)
        }
        stream.writeUTF(json.toString())
        logger.debug("Written imperium metadata: {}", json.toString())
    }

    override fun read(stream: DataInput) {
        val json = Json.parseToJsonElement(stream.readUTF()).jsonObject
        Vars.state.map.id = json[MAP_IDENTIFIER]?.jsonPrimitive?.int
        Vars.state.map.start = json[MAP_START]?.jsonPrimitive?.long?.let(Instant::fromEpochMilliseconds)
        Vars.state.map.playtime = json[MAP_PLAYTIME]?.jsonPrimitive?.long?.seconds ?: ZERO
        Vars.state.map.dayNightCycle = json[MAP_DAY_NIGHT_CYCLE]?.jsonPrimitive?.booleanOrNull ?: false
        logger.debug("Read imperium metadata: {}", json.toString())
    }
}
