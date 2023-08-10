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
package com.xpdustry.imperium.discord.content

import mindustry.game.Schematic
import reactor.core.publisher.Mono
import java.awt.image.BufferedImage
import java.io.InputStream

interface MindustryContentHandler {
    fun getSchematic(stream: InputStream): Mono<Schematic>
    fun getSchematic(string: String): Mono<Schematic>
    fun getSchematicPreview(schematic: Schematic): Mono<BufferedImage>
    fun getMapPreview(stream: InputStream): Mono<MapPreview>
}

data class MapPreview(
    val name: String,
    val description: String?,
    val author: String?,
    val tags: Map<String, String>,
    val image: BufferedImage,
)
