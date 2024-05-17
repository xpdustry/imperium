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
package com.xpdustry.imperium.mindustry.localization

import com.xpdustry.distributor.api.DistributorProvider
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.localization.LocalizationSource
import java.util.Locale

class DistributorLocalisationSource(private val config: ImperiumConfig) : LocalizationSource {
    override fun format(key: String, locale: Locale?, vararg arguments: Any): String =
        DistributorProvider.get()
            .globalTranslationSource
            .getTranslationOrMissing(key, config.language)
            .formatArray(*arguments)
}
