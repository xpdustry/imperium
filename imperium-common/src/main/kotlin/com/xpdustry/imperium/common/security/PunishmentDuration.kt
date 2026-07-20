// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

enum class PunishmentDuration(val value: Duration) {
    // Used as a kick
    NONE(1.nanoseconds),
    ONE_MINUTE(1.minutes),
    THIRTY_MINUTES(30.minutes),
    ONE_HOUR(1.hours),
    THREE_HOURS(3.hours),
    ONE_DAY(1.days),
    THREE_DAYS(3.days),
    ONE_WEEK(7.days),
    ONE_MONTH(30.days),
    PERMANENT(Duration.INFINITE),
}
