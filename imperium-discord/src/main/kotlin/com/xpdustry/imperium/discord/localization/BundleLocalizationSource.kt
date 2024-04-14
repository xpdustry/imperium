/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.imperium.discord.localization

import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.localization.LocalizationSource
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

class BundleLocalizationSource(private val config: ImperiumConfig) : LocalizationSource {
    override fun format(key: String, locale: Locale?, vararg arguments: Any): String {
        var result: String? = null
        if (locale != null) {
            result = getLocalization(key, locale, arguments)
        }
        if (result == null && locale != config.language) {
            result = getLocalization(key, config.language, arguments)
        }
        return result ?: "???$key???"
    }

    private fun getLocalization(key: String, locale: Locale, vararg arguments: Any) =
        ResourceBundle.getBundle(
                "com/xpdustry/imperium/bundles/bundle", locale, javaClass.classLoader)
            ?.takeIf { it.containsKey(key) }
            ?.getString(key)
            ?.let(::MessageFormat)
            ?.format(arguments)
}
