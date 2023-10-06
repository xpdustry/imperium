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
package com.xpdustry.imperium.common.storage

import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.net.URL
import java.time.Instant
import kotlin.time.Duration

interface StorageBucket {
    suspend fun getObject(root: String, vararg path: String): S3Object
    suspend fun listObjects(prefix: String = "", recursive: Boolean = false): Flow<S3Object>
    interface S3Object {
        val name: String get() = path.last()
        val path: List<String>
        val fullPath: String get() = path.joinToString("/")
        val size: Long
        val lastModified: Instant
        val exists: Boolean

        suspend fun putData(stream: InputStream)
        suspend fun getData(): InputStream
        suspend fun getDownloadUrl(expiration: Duration): URL
        suspend fun delete()
    }
}
