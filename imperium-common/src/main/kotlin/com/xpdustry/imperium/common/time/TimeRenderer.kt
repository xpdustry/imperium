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
package com.xpdustry.imperium.common.time

import com.xpdustry.imperium.common.config.ImperiumConfig
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import net.time4j.PrettyTime as Time4JavaPrettyTime
import org.ocpsoft.prettytime.PrettyTime as OCPSoftPrettyTime

interface TimeRenderer {
    fun renderDuration(duration: Duration, locale: Locale? = null): String

    fun renderInstant(instant: Instant): String

    fun renderRelativeInstant(instant: Instant, locale: Locale? = null): String
}

class SimpleTimeRenderer(private val config: ImperiumConfig) : TimeRenderer {

    override fun renderDuration(duration: Duration, locale: Locale?): String =
        if (duration.isInfinite()) "∞"
        else getTime4JavaPrettyTime(locale ?: config.language).print(duration.toJavaDuration())

    override fun renderInstant(instant: Instant): String = INSTANT_FORMAT.format(instant.atOffset(ZoneOffset.UTC))

    override fun renderRelativeInstant(instant: Instant, locale: Locale?): String =
        OCPSoftPrettyTime(Instant.now()).setLocale(locale ?: config.language).format(instant)

    private fun getTime4JavaPrettyTime(locale: Locale): Time4JavaPrettyTime {
        val classLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().setContextClassLoader(Time4JavaPrettyTime::class.java.classLoader)
        return try {
            Time4JavaPrettyTime.of(locale)
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader)
        }
    }

    companion object {
        private val INSTANT_FORMAT =
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral(' ')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .toFormatter()
    }
}
