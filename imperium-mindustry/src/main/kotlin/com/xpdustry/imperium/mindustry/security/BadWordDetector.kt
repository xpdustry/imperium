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

import com.xpdustry.imperium.common.application.ImperiumApplication
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.useLines
import okhttp3.OkHttpClient
import okhttp3.Request
import org.ahocorasick.trie.Emit
import org.ahocorasick.trie.Trie

interface BadWordDetector {

    // TODO Scope to specific language ?
    fun findBadWords(text: String): List<String>
}

class SimpleBadWordDetector(private val http: OkHttpClient) :
    BadWordDetector, ImperiumApplication.Listener {

    // TODO Try to implement custom aho-corasick if needed:
    // https://stackoverflow.com/questions/46921301/java-implementation-of-aho-corasick-string-matching-algorithm ?
    //
    private lateinit var trie: Trie

    override fun onImperiumInit() {
        http
            .newCall(
                Request.Builder()
                    .url("https://github.com/phinner/bad-words-list/archive/refs/heads/master.zip")
                    .build())
            .execute()
            .use { response ->
                if (response.code != 200) {
                    error("Failed to download bad words list")
                }
                val temp = Files.createTempFile("imperium", ".zip")
                temp.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
                val builder = Trie.builder().ignoreCase().stopOnHit()
                FileSystems.newFileSystem(temp, null as ClassLoader?).use { fs ->
                    fs.getPath("/bad-words-list-master/languages/")
                        .listDirectoryEntries()
                        .forEach { entry ->
                            entry.useLines { lines -> lines.forEach { builder.addKeyword(it) } }
                        }
                }
                trie = builder.build()
            }
    }

    override fun findBadWords(text: String) = trie.parseText(text).map(Emit::getKeyword)
}
