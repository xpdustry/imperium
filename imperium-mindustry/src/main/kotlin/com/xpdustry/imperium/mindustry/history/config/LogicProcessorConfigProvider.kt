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
package com.xpdustry.imperium.mindustry.history.config

import com.xpdustry.imperium.mindustry.history.HistoryEntry
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import com.xpdustry.imperium.mindustry.misc.asList
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.InflaterInputStream
import mindustry.world.blocks.logic.LogicBlock

object LogicProcessorConfigProvider : LinkableBlockConfigProvider<LogicBlock.LogicBuild>() {
    private const val MAX_INSTRUCTIONS_SIZE = 1024 * 500

    override fun create(building: LogicBlock.LogicBuild, type: HistoryEntry.Type, config: Any?) =
        if (
            type == HistoryEntry.Type.PLACING ||
                type == HistoryEntry.Type.PLACE ||
                type == HistoryEntry.Type.BREAKING ||
                type == HistoryEntry.Type.BREAK
        ) {
            getConfiguration(building)
        } else if (config is ByteArray) {
            readCode(config)?.let { BlockConfig.Text(it) }
        } else {
            super.create(building, type, config)
        }

    override fun isLinkValid(building: LogicBlock.LogicBuild, x: Int, y: Int): Boolean {
        val link = building.links.find { it.x == x && it.y == y }
        return link != null && link.active
    }

    private fun getConfiguration(building: LogicBlock.LogicBuild): BlockConfig? {
        val configurations = mutableListOf<BlockConfig>()
        val links =
            building.links
                .asList()
                .filter { it.active }
                .map { ImmutablePoint(it.x - building.tileX(), it.y - building.tileY()) }
                .toList()

        if (links.isNotEmpty()) {
            configurations += BlockConfig.Link(links, true)
        }
        if (building.code.isNotBlank()) {
            configurations += BlockConfig.Text(building.code)
        }

        return if (configurations.isEmpty()) {
            null
        } else if (configurations.size == 1) {
            configurations[0]
        } else {
            BlockConfig.Composite(configurations)
        }
    }

    private fun readCode(compressed: ByteArray): String? {
        try {
            DataInputStream(InflaterInputStream(ByteArrayInputStream(compressed))).use { stream ->
                val version: Int = stream.read()
                val length: Int = stream.readInt()
                if (length > MAX_INSTRUCTIONS_SIZE) {
                    return null
                }
                val bytes = ByteArray(length)
                stream.readFully(bytes)
                val links: Int = stream.readInt()
                if (version == 0) {
                    // old version just had links
                    for (i in 0 until links) {
                        stream.readInt()
                    }
                } else {
                    for (i in 0 until links) {
                        stream.readUTF() // name
                        stream.readShort() // x
                        stream.readShort() // y
                    }
                }
                return String(bytes, StandardCharsets.UTF_8)
            }
        } catch (exception: IOException) {
            return null
        }
    }
}
