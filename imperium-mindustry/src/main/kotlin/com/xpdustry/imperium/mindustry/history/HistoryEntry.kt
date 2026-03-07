// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.history

import com.xpdustry.imperium.mindustry.history.config.BlockConfig
import java.time.Instant
import mindustry.world.Block

data class HistoryEntry(
    val x: Int,
    val y: Int,
    val buildX: Int,
    val buildY: Int,
    val actor: HistoryActor,
    val block: Block,
    val type: Type,
    val rotation: Int,
    val config: BlockConfig? = null,
    val virtual: Boolean = false,
    val timestamp: Instant = Instant.now(),
) {
    enum class Type {
        PLACING,
        PLACE,
        BREAKING,
        ROTATE,
        BREAK,
        CONFIGURE,
    }
}
