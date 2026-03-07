// SPDX-License-Identifier: GPL-3.0-only
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
