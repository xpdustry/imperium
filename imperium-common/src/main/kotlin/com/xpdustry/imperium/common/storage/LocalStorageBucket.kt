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
package com.xpdustry.imperium.common.storage

import java.io.InputStream
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream

class LocalStorageBucket(private val directory: Path) : StorageBucket {

    override suspend fun getObject(first: String, vararg more: String): StorageObject = LocalStorageObject(first, more)

    private inner class LocalStorageObject(first: String, more: Array<out String>) : StorageObject {

        override val path: List<String> = listOf(first, *more)
        private val file: Path

        init {
            var f = directory.resolve(first)
            for (m in more) f = f.resolve(m)
            file = f
        }

        override val size: Long
            get() = file.fileSize()

        override val lastModified: Instant
            get() = file.getLastModifiedTime().toInstant()

        override val exists: Boolean
            get() = file.exists() && file.isRegularFile()

        override suspend fun putData(stream: InputStream) {
            file.createParentDirectories()
            file.outputStream().use { stream.copyTo(it) }
        }

        override suspend fun getData() = file.inputStream()

        override suspend fun delete() {
            file.deleteIfExists()
            var parent = file.parent
            while (parent != null && parent != directory && parent.listDirectoryEntries().isEmpty()) {
                parent.deleteExisting()
                parent = parent.parent
            }
        }
    }
}
