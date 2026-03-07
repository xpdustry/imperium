// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.time

import java.time.temporal.TemporalUnit
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

fun Duration.truncatedTo(unit: TemporalUnit) = toJavaDuration().truncatedTo(unit).toKotlinDuration()
