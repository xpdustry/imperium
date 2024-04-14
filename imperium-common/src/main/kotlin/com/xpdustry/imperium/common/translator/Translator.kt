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
package com.xpdustry.imperium.common.translator

import java.util.Locale

interface Translator {
    suspend fun translate(text: String, source: Locale, target: Locale): TranslatorResult

    fun isSupportedLanguage(locale: Locale): Boolean

    object Noop : Translator {
        override suspend fun translate(text: String, source: Locale, target: Locale) =
            TranslatorResult.UnsupportedLanguage(target)

        override fun isSupportedLanguage(locale: Locale) = false
    }
}

sealed interface TranslatorResult {
    data class Success(val text: String) : TranslatorResult

    data class Failure(val exception: Exception) : TranslatorResult

    data class UnsupportedLanguage(val locale: Locale) : TranslatorResult

    data object RateLimited : TranslatorResult
}
