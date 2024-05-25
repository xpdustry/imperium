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

import com.xpdustry.distributor.api.audience.Audience
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.render.ComponentAppendable
import com.xpdustry.distributor.api.component.render.ComponentRenderer
import com.xpdustry.distributor.api.component.render.ComponentRendererProvider
import com.xpdustry.distributor.api.metadata.MetadataContainer
import com.xpdustry.imperium.common.time.TimeRenderer
import java.util.Locale
import kotlin.jvm.optionals.getOrNull

class ImperiumComponentRendererProvider(private val renderer: TimeRenderer) :
    ComponentRendererProvider {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Component> getRenderer(component: T) =
        when (component) {
            is DurationComponent -> DurationComponentRenderer(renderer) as ComponentRenderer<T>
            else -> null
        }

    class DurationComponentRenderer(private val renderer: TimeRenderer) :
        ComponentRenderer<DurationComponent> {
        override fun render(
            component: DurationComponent,
            appendable: ComponentAppendable,
            metadata: MetadataContainer
        ) {
            appendable.append(
                renderer.renderDuration(
                    component.duration,
                    metadata.getMetadata(Audience.LOCALE).getOrNull() ?: Locale.ENGLISH))
        }
    }
}
