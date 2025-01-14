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
package com.xpdustry.imperium.mindustry.component

import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.render.ComponentRenderer
import com.xpdustry.distributor.api.component.render.ComponentRendererProvider
import com.xpdustry.distributor.api.component.render.ComponentStringBuilder
import com.xpdustry.distributor.api.key.StandardKeys
import com.xpdustry.imperium.common.time.TimeRenderer
import java.util.Locale

class ImperiumComponentRendererProvider(renderer: TimeRenderer) : ComponentRendererProvider {

    private val duration = DurationComponentRenderer(renderer)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Component> getRenderer(component: T) =
        when (component) {
            is DurationComponent -> duration as ComponentRenderer<T>
            else -> null
        }

    class DurationComponentRenderer(private val renderer: TimeRenderer) : ComponentRenderer<DurationComponent> {
        override fun render(component: DurationComponent, builder: ComponentStringBuilder) {
            builder.append(
                renderer.renderDuration(component.duration, builder.context.get(StandardKeys.LOCALE) ?: Locale.ENGLISH)
            )
        }
    }
}
