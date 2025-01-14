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
package com.xpdustry.imperium.mindustry.ui.transform

import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.mindustry.ui.Pane
import com.xpdustry.imperium.mindustry.ui.View

class PriorityTransformer<P : Pane>(private val transformer: Transformer<P>, private val priority: Priority) :
    Transformer<P>, Comparable<PriorityTransformer<*>> {

    override fun transform(view: View, pane: P) {
        transformer.transform(view, pane)
    }

    override fun compareTo(other: PriorityTransformer<*>): Int {
        return priority.compareTo(other.priority)
    }
}
