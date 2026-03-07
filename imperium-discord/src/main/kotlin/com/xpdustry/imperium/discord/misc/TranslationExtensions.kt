// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.misc

import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle
import net.dv8tion.jda.api.interactions.DiscordLocale

fun getTranslatedTextOrNull(key: String, locale: Locale): String? =
    try {
        ResourceBundle.getBundle("com/xpdustry/imperium/discord/bundles/bundle", locale).getString(key)
    } catch (error: MissingResourceException) {
        null
    }

fun Locale.toDiscordLocale(): DiscordLocale? {
    val locale = DiscordLocale.from(this)
    if (locale != DiscordLocale.UNKNOWN) {
        return locale
    }
    return when (language) {
        "en" -> DiscordLocale.ENGLISH_US
        "es" -> DiscordLocale.SPANISH
        "pt" -> DiscordLocale.PORTUGUESE_BRAZILIAN
        "zh" -> DiscordLocale.CHINESE_CHINA
        else -> null
    }
}
