// SPDX-License-Identifier: GPL-3.0-only
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
