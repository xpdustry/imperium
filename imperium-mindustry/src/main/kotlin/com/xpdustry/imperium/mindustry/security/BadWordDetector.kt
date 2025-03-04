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
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.imperium.common.collection.CharTrieMap
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.common.misc.LoggerDelegate
import jakarta.inject.Inject
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request

interface BadWordDetector {

    fun findBadWords(text: String, categories: Set<Category>): List<String>
}

enum class Category {
    STRONG_LANGUAGE,
    SEXUAL,
    HATE_SPEECH,
}

class SimpleBadWordDetector @Inject constructor(private val http: OkHttpClient, private val config: ImperiumConfig) :
    BadWordDetector, LifecycleListener {

    private val trie = CharTrieMap.create<Category>()

    @OptIn(ExperimentalSerializationApi::class)
    override fun onImperiumInit() {
        try {
            http
                .newCall(
                    Request.Builder().url("https://github.com/xpdustry/bad-words/archive/refs/heads/master.zip").build()
                )
                .execute()
                .use { response ->
                    if (response.code != 200) {
                        error("Failed to download bad words list")
                    }
                    val temp = Files.createTempFile("imperium", ".zip")
                    temp.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
                    FileSystems.newFileSystem(temp, null as ClassLoader?).use { fs ->
                        fs.getPath("/bad-words-master/languages/").listDirectoryEntries().forEach { entry ->
                            entry.inputStream().use {
                                for (category in Json.decodeFromStream<List<BadWordCategory>>(it)) {
                                    val parsed =
                                        when (category.category) {
                                            "hate" -> Category.HATE_SPEECH
                                            "sexual" -> Category.SEXUAL
                                            "strong" -> Category.STRONG_LANGUAGE
                                            else -> continue
                                        }
                                    category.words.forEach { word -> trie.put(word.lowercase().toCharArray(), parsed) }
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            if (config.testing) {
                logger.warn("Failed to download bad words list", e)
            } else {
                throw e
            }
        }
    }

    override fun findBadWords(text: String, categories: Set<Category>) =
        trie
            .search(text.lowercase())
            .filter {
                it.value in categories &&
                    (it.word.length >= 5 ||
                        (text.getOrNull(it.index - 1)?.isWhitespace() ?: true &&
                            text.getOrNull(it.index + it.word.length)?.isWhitespace() ?: true))
            }
            .map { it.word }

    @Serializable private data class BadWordCategory(val category: String, val words: Set<String>)

    companion object {
        private val logger by LoggerDelegate()
    }
}
