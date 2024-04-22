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
package com.xpdustry.imperium.common.translator

import com.deepl.api.Formality
import com.deepl.api.LanguageType
import com.deepl.api.TextTranslationOptions
import com.deepl.api.TranslatorOptions
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.TranslatorConfig
import com.xpdustry.imperium.common.version.ImperiumVersion
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class DeeplTranslator(config: ImperiumConfig, version: ImperiumVersion) :
    Translator, ImperiumApplication.Listener {
    private val translator: com.deepl.api.Translator
    private val cache: Cache<TranslatorKey, String>
    private lateinit var sourceLanguages: List<Locale>
    private lateinit var targetLanguages: List<Locale>

    init {
        if (config.translator !is TranslatorConfig.DeepL) {
            throw IllegalArgumentException("Invalid translator config")
        }
        translator =
            com.deepl.api.Translator(
                config.translator.token.value,
                TranslatorOptions()
                    .setTimeout(Duration.ofSeconds(3L))
                    .setAppInfo("Imperium", version.toString()),
            )
        cache =
            CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build()
    }

    override fun onImperiumInit() =
        runBlocking(ImperiumScope.MAIN.coroutineContext) {
            sourceLanguages = fetchLanguages(LanguageType.Source)
            targetLanguages = fetchLanguages(LanguageType.Target)
        }

    override suspend fun translate(text: String, source: Locale, target: Locale): TranslatorResult {
        if (source.language == "router" || target.language == "router") {
            return TranslatorResult.Success("router")
        }
        if (text.isBlank()) {
            return TranslatorResult.Success(text)
        }

        val sourceLocale =
            findClosestLanguage(LanguageType.Source, source)
                ?: return TranslatorResult.UnsupportedLanguage(source)
        val targetLocale =
            findClosestLanguage(LanguageType.Target, target)
                ?: return TranslatorResult.UnsupportedLanguage(target)

        if (sourceLocale.language == targetLocale.language) {
            return TranslatorResult.Success(text)
        }

        val key = TranslatorKey(text, sourceLocale, targetLocale)
        val cached = cache.getIfPresent(key)
        if (cached != null) {
            return TranslatorResult.Success(cached)
        }

        if (fetchRateLimited()) {
            return TranslatorResult.RateLimited
        }

        return withContext(ImperiumScope.MAIN.coroutineContext) {
            val result =
                try {
                    translator
                        .translateText(
                            key.text,
                            key.source.language,
                            key.target.toLanguageTag(),
                            DEFAULT_OPTIONS)
                        .text
                } catch (e: Exception) {
                    return@withContext TranslatorResult.Failure(e)
                }
            cache.put(key, result)
            TranslatorResult.Success(result)
        }
    }

    override fun isSupportedLanguage(locale: Locale): Boolean =
        findClosestLanguage(LanguageType.Source, locale) != null

    private fun findClosestLanguage(type: LanguageType, locale: Locale): Locale? {
        val languages =
            when (type) {
                LanguageType.Source -> sourceLanguages
                LanguageType.Target -> targetLanguages
            }
        val candidates = languages.filter { locale.language == it.language }
        return if (candidates.isEmpty()) {
            null
        } else if (candidates.size == 1) {
            candidates[0]
        } else {
            candidates.find { locale.country == it.country } ?: candidates[0]
        }
    }

    private suspend fun fetchLanguages(type: LanguageType) =
        withContext(ImperiumScope.IO.coroutineContext) {
            translator.getLanguages(type).map { Locale.forLanguageTag(it.code) }
        }

    private suspend fun fetchRateLimited() =
        withContext(ImperiumScope.IO.coroutineContext) {
            translator.usage.character!!.limitReached()
        }

    data class TranslatorKey(val text: String, val source: Locale, val target: Locale)

    companion object {
        private val DEFAULT_OPTIONS = TextTranslationOptions().setFormality(Formality.PreferLess)
    }
}
