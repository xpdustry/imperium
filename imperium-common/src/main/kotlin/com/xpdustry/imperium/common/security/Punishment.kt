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
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.misc.MindustryUUIDAsLong
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.timestamp
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class Punishment(
    val snowflake: Snowflake,
    val target: Snowflake,
    val reason: String,
    val type: Type,
    val duration: Duration,
    val pardon: Pardon?,
    val server: String,
    val metadata: Metadata,
) {
    val expired: Boolean
        get() = pardon != null || expiration < Instant.now()

    val expiration: Instant
        get() =
            if (duration.isInfinite()) Instant.MAX
            else snowflake.timestamp.plus(duration.toJavaDuration())

    val permanent: Boolean
        get() = duration.isInfinite()

    data class Pardon(val timestamp: Instant, val reason: String)

    enum class Type {
        MUTE,
        BAN
    }

    sealed interface Metadata {
        data object None : Metadata

        data class Votekick(val yes: Set<MindustryUUIDAsLong>, val nay: Set<MindustryUUIDAsLong>) :
            Metadata
    }
}

fun encodeMetadata(metadata: Punishment.Metadata): String =
    buildJsonObject {
            when (metadata) {
                is Punishment.Metadata.None -> Unit
                is Punishment.Metadata.Votekick -> {
                    put("type", "votekick")
                    putJsonArray("yes") { metadata.yes.forEach(this@putJsonArray::add) }
                    putJsonArray("nay") { metadata.nay.forEach(this@putJsonArray::add) }
                }
            }
        }
        .toString()

fun decodeMetadata(metadata: String): Punishment.Metadata =
    Json.parseToJsonElement(metadata).let { element ->
        val obj = element.jsonObject
        return@let when (val type = obj["type"]?.jsonPrimitive?.content ?: "none") {
            "none" -> Punishment.Metadata.None
            "votekick" -> {
                val yes = obj["yes"]!!.jsonArray.mapTo(mutableSetOf()) { it.jsonPrimitive.long }
                val nay = obj["nay"]!!.jsonArray.mapTo(mutableSetOf()) { it.jsonPrimitive.long }
                Punishment.Metadata.Votekick(yes, nay)
            }
            else -> error("Unknown metadata type: $type")
        }
    }
