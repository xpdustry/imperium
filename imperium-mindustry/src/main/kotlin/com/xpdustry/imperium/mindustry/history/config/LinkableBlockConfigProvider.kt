// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.history.config

import arc.math.geom.Point2
import com.xpdustry.imperium.mindustry.history.HistoryEntry
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import mindustry.gen.Building

abstract class LinkableBlockConfigProvider<B : Building> : BlockConfig.Provider<B> {
    override fun create(building: B, type: HistoryEntry.Type, config: Any?): BlockConfig? {
        if (config == null || !building.block.configurations.containsKey(config.javaClass)) {
            return null
        }
        return if (config is Int) {
            if (config == -1 || config == building.pos()) {
                return BlockConfig.Reset
            }
            val point = Point2.unpack(config)
            if (point.x < 0 || point.y < 0) {
                null
            } else
                BlockConfig.Link(
                    listOf(ImmutablePoint(point.x - building.tileX(), point.y - building.tileY())),
                    isLinkValid(building, point.x, point.y),
                )
        } else if (config is Point2) {
            // Point2 are used by schematics, so they are already relative to the building
            BlockConfig.Link(
                listOf(ImmutablePoint(config.x, config.y)),
                isLinkValid(building, config.x + building.tileX(), config.y + building.tileY()),
            )
        } else if (config is Array<*> && config.isArrayOf<Point2>()) {
            BlockConfig.Link(
                config.map {
                    it as Point2
                    ImmutablePoint(it.x, it.y)
                },
                true,
            )
        } else {
            null
        }
    }

    protected abstract fun isLinkValid(building: B, x: Int, y: Int): Boolean
}
