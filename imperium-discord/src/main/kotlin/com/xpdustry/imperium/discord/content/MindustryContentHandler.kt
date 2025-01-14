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
package com.xpdustry.imperium.discord.content

import java.awt.image.BufferedImage
import java.io.InputStream
import java.io.OutputStream
import mindustry.game.Schematic

interface MindustryContentHandler {
    suspend fun getSchematic(stream: InputStream): Result<Schematic>

    suspend fun getSchematic(string: String): Result<Schematic>

    suspend fun getSchematicPreview(schematic: Schematic): Result<BufferedImage>

    suspend fun writeSchematic(schematic: Schematic, output: OutputStream): Result<Unit>

    suspend fun getMapMetadata(stream: InputStream): Result<MapMetadata>

    suspend fun getMapMetadataWithPreview(stream: InputStream): Result<Pair<MapMetadata, BufferedImage>>
}

data class MapMetadata(
    val name: String,
    val description: String?,
    val author: String?,
    val width: Int,
    val height: Int,
    val tags: Map<String, String>,
)
