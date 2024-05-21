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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.TranslatorConfig
import com.xpdustry.imperium.common.network.await
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

class LibreTranslateTranslator(
    private val config: TranslatorConfig.LibreTranslate,
    private val http: OkHttpClient
) : Translator, ImperiumApplication.Listener {

    private lateinit var languages: Set<String>

    override fun onImperiumInit() {
        runBlocking {
            languages = getSupportedLanguages().mapTo(mutableSetOf(), SupportedLanguage::code)
        }
    }

    override suspend fun translate(text: String, source: Locale, target: Locale): TranslatorResult {
        val supported =
            try {
                getSupportedLanguages()
            } catch (e: Exception) {
                return TranslatorResult.Failure(e)
            }

        val candidate = supported.firstOrNull { it.code == source.language }
        if (candidate == null) {
            return TranslatorResult.UnsupportedLanguage(source)
        } else if (target.language !in candidate.targets) {
            return TranslatorResult.UnsupportedLanguage(target)
        }

        val response =
            http
                .newCall(
                    Request.Builder()
                        .url(
                            config.ltEndpoint
                                .toHttpUrlOrNull()!!
                                .newBuilder()
                                .addPathSegment("translate")
                                .build())
                        .post(
                            MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("q", text)
                                .addFormDataPart("source", source.language)
                                .addFormDataPart("target", target.language)
                                .addFormDataPart("api_key", config.ltToken.value)
                                .addFormDataPart("format", "text")
                                .build())
                        .build())
                .await()

        val json = Json.parseToJsonElement(response.body!!.string()).jsonObject
        if (response.code != 200) {
            return TranslatorResult.Failure(
                Exception("Failed to translate: ${json["error"]} (code=${response.code})"))
        }

        return TranslatorResult.Success(json["translatedText"]!!.jsonPrimitive.content)
    }

    override fun isSupportedLanguage(locale: Locale) = locale.language in languages

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun getSupportedLanguages(): List<SupportedLanguage> {
        val response =
            http
                .newCall(
                    Request.Builder()
                        .url(
                            config.ltEndpoint
                                .toHttpUrlOrNull()!!
                                .newBuilder()
                                .addPathSegment("languages")
                                .build())
                        .header("Accept", "application/json")
                        .get()
                        .build())
                .await()
        if (response.code != 200) {
            error(
                "Failed to fetch supported languages: ${response.message} (code=${response.code})")
        }
        return response.body!!.source().inputStream().use {
            Json.decodeFromStream<List<SupportedLanguage>>(it)
        }
    }

    @Serializable
    data class SupportedLanguage(val code: String, val name: String, val targets: Set<String>)
}
