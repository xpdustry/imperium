// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.time

import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import java.time.Instant as JavaInstant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Instant as KotlinInstant
import kotlin.time.toJavaDuration
import kotlin.time.toJavaInstant
import net.time4j.PrettyTime as Time4JavaPrettyTime
import org.ocpsoft.prettytime.PrettyTime as OCPSoftPrettyTime

// TODO Refactor this... thing...
interface TimeRenderer {
    fun renderDuration(duration: Duration, locale: Locale? = null): String

    fun renderInstant(instant: KotlinInstant): String

    fun renderRelativeInstant(instant: KotlinInstant, locale: Locale? = null): String
}

@Inject
class SimpleTimeRenderer(private val config: ImperiumConfig) : TimeRenderer {

    override fun renderDuration(duration: Duration, locale: Locale?): String =
        if (duration.isInfinite()) "∞"
        else getTime4JavaPrettyTime(locale ?: config.language).print(duration.toJavaDuration())

    override fun renderInstant(instant: KotlinInstant): String =
        INSTANT_FORMAT.format(instant.toJavaInstant().atOffset(ZoneOffset.UTC))

    override fun renderRelativeInstant(instant: KotlinInstant, locale: Locale?): String =
        OCPSoftPrettyTime(JavaInstant.now()).setLocale(locale ?: config.language).format(instant.toJavaInstant())

    private fun getTime4JavaPrettyTime(locale: Locale): Time4JavaPrettyTime {
        val classLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = Time4JavaPrettyTime::class.java.classLoader
        return try {
            Time4JavaPrettyTime.of(locale)
        } finally {
            Thread.currentThread().contextClassLoader = classLoader
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
