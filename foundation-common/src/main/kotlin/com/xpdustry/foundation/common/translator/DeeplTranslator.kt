/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.common.translator

import com.deepl.api.Formality
import com.deepl.api.LanguageType
import com.deepl.api.TextTranslationOptions
import com.deepl.api.TranslatorOptions
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.application.FoundationMetadata
import com.xpdustry.foundation.common.configuration.FoundationConfig
import com.xpdustry.foundation.common.misc.RateLimitException
import com.xpdustry.foundation.common.misc.switchIfEmpty
import com.xpdustry.foundation.common.misc.toErrorMono
import com.xpdustry.foundation.common.misc.toValueMono
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.Locale

// So clean!
class DeeplTranslator @Inject constructor(config: FoundationConfig, metadata: FoundationMetadata) : Translator, FoundationListener {

    private val translator: com.deepl.api.Translator?
    private val cache: Cache<TranslatorKey, String>
    private lateinit var sourceLanguages: List<Locale>
    private lateinit var targetLanguages: List<Locale>

    init {
        translator = config.translator.deepl?.let {
            com.deepl.api.Translator(
                it.value,
                TranslatorOptions()
                    .setTimeout(Duration.ofSeconds(3L))
                    .setAppInfo("Foundation", metadata.version.toString()),
            )
        }

        cache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build()
    }

    override fun onFoundationInit() {
        sourceLanguages = fetchLanguages(LanguageType.Source).block()!!
        targetLanguages = fetchLanguages(LanguageType.Target).block()!!
    }

    override fun translate(text: String, source: Locale, target: Locale): Mono<String> {
        if (source.language == "router" || target.language == "router") {
            return "router".toValueMono()
        }

        if (text.isBlank()) {
            return text.toValueMono()
        }

        val sourceLocale = findClosestLanguage(LanguageType.Source, source)
            ?: return UnsupportedLocaleException(source).toErrorMono()
        val targetLocale = findClosestLanguage(LanguageType.Target, target)
            ?: return UnsupportedLocaleException(target).toErrorMono()

        if (sourceLocale.language == targetLocale.language) {
            return text.toValueMono()
        }

        val key = TranslatorKey(text, sourceLocale, targetLocale)

        return cache.getIfPresent(key).toValueMono().switchIfEmpty {
            fetchRateLimited()
                .filter { limited -> limited.not() }
                .switchIfEmpty { RateLimitException().toErrorMono() }
                .map {
                    translator!!.translateText(
                        key.text,
                        key.source.language,
                        key.target.toLanguageTag(),
                        DEFAULT_OPTIONS,
                    ).text
                }
                .doOnNext { cache.put(key, it) }
        }
    }

    override fun isSupportedLanguage(locale: Locale): Boolean {
        return findClosestLanguage(LanguageType.Source, locale) != null
    }

    private fun findClosestLanguage(type: LanguageType, locale: Locale): Locale? {
        val languages = when (type) {
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

    private fun fetchLanguages(type: LanguageType): Mono<List<Locale>> = Mono.fromSupplier {
        translator?.getLanguages(type)?.map { Locale.forLanguageTag(it.code) } ?: emptyList()
    }

    private fun fetchRateLimited(): Mono<Boolean> = Mono.fromSupplier {
        translator?.usage?.character?.limitReached() ?: true
    }

    data class TranslatorKey(val text: String, val source: Locale, val target: Locale)

    companion object {
        private val DEFAULT_OPTIONS = TextTranslationOptions().setFormality(Formality.PreferLess)
    }
}
