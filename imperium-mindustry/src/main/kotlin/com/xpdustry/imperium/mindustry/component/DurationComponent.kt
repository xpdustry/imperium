// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.component

import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.component.style.TextStyle
import kotlin.time.Duration

fun duration(duration: Duration, textStyle: TextStyle) = DurationComponent(duration, textStyle)

fun duration(duration: Duration, textColor: ComponentColor) = DurationComponent(duration, TextStyle.of(textColor))

data class DurationComponent(val duration: Duration, private val textStyle: TextStyle) : Component {
    override fun getTextStyle() = textStyle
}
