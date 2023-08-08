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

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.InputStream
import java.net.URL
import java.time.Duration
import java.time.Instant

interface Storage {
    fun getBucket(name: String, create: Boolean = false): Mono<Bucket>
    fun listBuckets(): Flux<Bucket>
    fun deleteBucket(name: String): Mono<Void>
}

interface Bucket {
    val name: String
    fun getObject(name: String): Mono<S3Object>
    fun putObject(name: String, stream: InputStream): Mono<Void>
    fun listObjects(prefix: String = "", recursive: Boolean = false): Flux<S3Object>
    fun deleteObject(name: String): Mono<Void>
}

interface S3Object {
    val name: String get() = path.last()
    val path: List<String>
    val size: Long
    val lastModified: Instant
    fun getStream(): Mono<InputStream>
    fun getDownloadUrl(expiration: Duration): Mono<URL>
}
