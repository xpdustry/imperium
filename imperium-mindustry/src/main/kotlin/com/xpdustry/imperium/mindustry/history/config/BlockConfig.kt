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
import java.nio.ByteBuffer
import mindustry.ctype.MappableContent

sealed interface BlockConfig {

    data class Composite(val configs: List<BlockConfig>) : BlockConfig

    data class Enable(val value: Boolean) : BlockConfig

    data class Content(val value: MappableContent) : BlockConfig

    data class Link(val positions: List<ImmutablePoint>, val connection: Boolean) : BlockConfig

    data class Text(val text: String) : BlockConfig

    data class Light(val color: Int) : BlockConfig

    data class Canvas(val content: ByteBuffer) : BlockConfig

    data object Reset : BlockConfig

    fun interface Provider<B> {
        fun create(building: B, type: HistoryEntry.Type, config: Any?): BlockConfig?
    }
}
