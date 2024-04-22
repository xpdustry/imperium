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
package com.xpdustry.imperium.mindustry.history.factory

import arc.math.geom.Point2
import com.xpdustry.imperium.mindustry.history.HistoryConfig
import com.xpdustry.imperium.mindustry.history.HistoryEntry
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import mindustry.gen.Building

abstract class LinkableBlockConfigurationFactory<B : Building> : HistoryConfig.Factory<B> {
    override fun create(building: B, type: HistoryEntry.Type, config: Any?): HistoryConfig? {
        if (config == null || !building.block().configurations.containsKey(config.javaClass)) {
            return null
        }
        return if (config is Int) {
            if (config == -1 || config == building.pos()) {
                return HistoryConfig.Link(emptyList(), HistoryConfig.Link.Type.RESET)
            }
            val point = Point2.unpack(config)
            if (point.x < 0 || point.y < 0) {
                null
            } else
                HistoryConfig.Link(
                    listOf(ImmutablePoint(point.x - building.tileX(), point.y - building.tileY())),
                    isLinkValid(building, point.x, point.y),
                )
        } else if (config is Point2) {
            // Point2 are used by schematics, so they are already relative to the building
            HistoryConfig.Link(
                listOf(ImmutablePoint(config.x, config.y)),
                isLinkValid(building, config.x + building.tileX(), config.y + building.tileY()),
            )
        } else if (config is Array<*> && config.isArrayOf<Point2>()) {
            HistoryConfig.Link(
                config.map {
                    it as Point2
                    ImmutablePoint(it.x, it.y)
                },
                true)
        } else {
            null
        }
    }

    protected abstract fun isLinkValid(building: B, x: Int, y: Int): Boolean
}
