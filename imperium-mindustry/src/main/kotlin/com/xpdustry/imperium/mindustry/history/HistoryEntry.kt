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
