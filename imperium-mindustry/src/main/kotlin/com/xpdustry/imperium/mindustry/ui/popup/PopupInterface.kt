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
package com.xpdustry.imperium.mindustry.ui.popup

import arc.util.Interval
import arc.util.Time
import com.xpdustry.imperium.mindustry.ui.AbstractTransformerInterface
import com.xpdustry.imperium.mindustry.ui.TransformerInterface
import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import mindustry.game.EventType
import mindustry.gen.Call

interface PopupInterface : TransformerInterface<PopupPane> {
    var updateInterval: Int

    companion object {
        fun create(plugin: MindustryPlugin): PopupInterface {
            return SimplePopupInterface(plugin)
        }
    }
}

private class SimplePopupInterface(
    plugin: MindustryPlugin,
) : AbstractTransformerInterface<PopupPane>(plugin, ::PopupPane), PopupInterface {

    private val interval = Interval()
    override var updateInterval = 60

    init {
        interval.reset(0, Float.MAX_VALUE)
        DistributorProvider.get().eventBus.subscribe(EventType.Trigger.update, plugin) {
            if (interval[updateInterval.toFloat()]) {
                for (view in views.values) {
                    view.open()
                }
            }
        }
    }

    override fun onViewOpen(view: SimpleView) {
        Call.infoPopup(
            view.viewer.con(),
            view.pane.content,
            // Don't even ask me why this works, I don't know either
            Time.delta / 60f * updateInterval,
            view.pane.alignement.align,
            0,
            0,
            view.pane.shiftY,
            view.pane.shiftX,
        )
    }
}
