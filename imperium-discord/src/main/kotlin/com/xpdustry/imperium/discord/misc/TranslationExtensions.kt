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
