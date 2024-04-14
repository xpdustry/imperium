/*
 * Imperium, the software collection powering the Xpdustry network.
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
 *
 */
package com.xpdustry.imperium.mindustry.history

import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import java.awt.Color
import java.nio.ByteBuffer
import mindustry.ctype.UnlockableContent
import mindustry.gen.Building

sealed interface HistoryConfig {

    data class Enable(val value: Boolean) : HistoryConfig

    data class Content(val value: UnlockableContent?) : HistoryConfig

    data class Link(val positions: List<ImmutablePoint>, val type: Type) : HistoryConfig {
        constructor(
            positions: List<ImmutablePoint>,
            connection: Boolean
        ) : this(positions, if (connection) Type.CONNECT else Type.DISCONNECT)

        enum class Type {
            CONNECT,
            DISCONNECT,
            RESET
        }
    }

    data class Composite(val configurations: List<HistoryConfig>) : HistoryConfig {
        init {
            for (configuration in configurations) {
                require(configuration !is Composite) {
                    "A Composite configuration cannot contain another."
                }
            }
        }
    }

    data class Simple(val value: Any?) : HistoryConfig

    data class Text(val text: String, val type: Type) : HistoryConfig {
        enum class Type {
            MESSAGE,
            CODE,
        }
    }

    data class Light(val color: Color) : HistoryConfig

    data class Canvas(val bytes: ByteBuffer) : HistoryConfig {
        constructor(bytes: ByteArray) : this(ByteBuffer.wrap(bytes.clone()))
    }

    fun interface Factory<B : Building> {
        fun create(building: B, type: HistoryEntry.Type, config: Any?): HistoryConfig?
    }
}
