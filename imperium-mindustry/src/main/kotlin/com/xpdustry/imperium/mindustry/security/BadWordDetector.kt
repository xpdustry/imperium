// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.dependency.Inject
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
import org.ahocorasick.trie.PayloadTrie

interface BadWordDetector {

    fun findBadWords(text: String, categories: Set<Category>): List<String>
}

enum class Category {
    STRONG_LANGUAGE,
    SEXUAL,
    HATE_SPEECH,
}

@Inject
class SimpleBadWordDetector(private val http: OkHttpClient) : BadWordDetector, ImperiumApplication.Listener {

    private lateinit var trie: PayloadTrie<Category>

    @OptIn(ExperimentalSerializationApi::class)
    override fun onImperiumInit() {
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
                temp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                val builder = PayloadTrie.builder<Category>().ignoreCase()
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
                                category.words.forEach { word -> builder.addKeyword(word, parsed) }
                            }
                        }
                    }
                }
                trie = builder.build()
            }
    }

    override fun findBadWords(text: String, categories: Set<Category>) =
        trie
            .parseText(text)
            .filter {
                it.payload in categories &&
                    (it.keyword.length >= 5 ||
                        (text.getOrNull(it.start - 1)?.isWhitespace() ?: true &&
                            text.getOrNull(it.end + 1)?.isWhitespace() ?: true))
            }
            .map { it.keyword }

    @Serializable private data class BadWordCategory(val category: String, val words: Set<String>)
}
