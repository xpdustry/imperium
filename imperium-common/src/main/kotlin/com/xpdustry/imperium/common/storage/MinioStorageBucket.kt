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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.StorageConfig
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.ListObjectsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioAsyncClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import io.minio.http.Method
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.InputStream
import java.net.URL
import java.time.Instant
import java.util.concurrent.CompletionException
import kotlin.time.Duration

class MinioStorageBucket(private val config: StorageConfig.Minio, private val http: OkHttpClient) : StorageBucket, ImperiumApplication.Listener {
    private lateinit var client: MinioAsyncClient

    override fun onImperiumInit() {
        client = MinioAsyncClient.builder()
            .endpoint(config.host, config.port, config.secure)
            .credentials(config.accessKey.value, config.secretKey.value)
            .httpClient(http)
            .build()

        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(config.bucket).build()).join()) {
                client.makeBucket(MakeBucketArgs.builder().bucket(config.bucket).build()).join()
            }
        } catch (e: Exception) {
            throw RuntimeException("Could not connect to Minio", e)
        }
    }

    override suspend fun getObject(root: String, vararg path: String): StorageBucket.S3Object =
        MinioS3Object(listOf(root, *path)).also { it.update() }

    override suspend fun listObjects(prefix: String, recursive: Boolean): Flow<StorageBucket.S3Object> =
        withContext(ImperiumScope.IO.coroutineContext) {
            flow {
                val results = client.listObjects(
                    ListObjectsArgs.builder()
                        .bucket(config.bucket)
                        .prefix(prefix)
                        .recursive(recursive).build(),
                )
                for (result in results) {
                    val entry = result.get()
                    val obj = MinioS3Object(entry.objectName().split("/"))
                    obj.size = entry.size()
                    obj.lastModified = entry.lastModified().toInstant()
                    obj.exists = true
                    emit(obj)
                }
            }
        }

    private inner class MinioS3Object(override val path: List<String>) : StorageBucket.S3Object {
        override var lastModified: Instant = Instant.EPOCH
        override var size: Long = 0
        override var exists: Boolean = false

        override suspend fun putData(stream: InputStream) =
            withContext(ImperiumScope.IO.coroutineContext) {
                client.putObject(
                    PutObjectArgs.builder()
                        .bucket(config.bucket)
                        .`object`(fullPath)
                        .stream(stream, -1, DEFAULT_PART_SIZE)
                        .build(),
                ).await()
                update()
            }

        override suspend fun getData(): InputStream =
            withContext(ImperiumScope.IO.coroutineContext) {
                client.getObject(
                    GetObjectArgs.builder()
                        .bucket(config.bucket)
                        .`object`(fullPath)
                        .build(),
                ).await()
            }

        override suspend fun getDownloadUrl(expiration: Duration): URL =
            withContext(ImperiumScope.IO.coroutineContext) {
                URL(
                    client.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(config.bucket)
                            .`object`(fullPath)
                            .expiry(expiration.inWholeSeconds.toInt())
                            .build(),
                    ),
                )
            }

        override suspend fun delete() =
            withContext(ImperiumScope.IO.coroutineContext) {
                client.removeObject(RemoveObjectArgs.builder().bucket(config.bucket).`object`(fullPath).build()).await()
                update()
            }

        suspend fun update() =
            withContext(ImperiumScope.IO.coroutineContext) {
                val stats = try {
                    client.statObject(
                        StatObjectArgs.builder()
                            .bucket(config.bucket)
                            .`object`(fullPath)
                            .build(),
                    ).await()
                } catch (e: CompletionException) {
                    // For some reason, the real exception is wrapped withing multiple CompletionException
                    var exception: Throwable = e
                    while (exception is CompletionException && exception.cause != null) {
                        exception = exception.cause!!
                    }
                    if (exception is ErrorResponseException && exception.errorResponse().code() != "NoSuchKey") {
                        throw e
                    }
                    null
                }
                if (stats != null) {
                    exists = true
                    lastModified = stats.lastModified().toInstant()
                    size = stats.size()
                } else {
                    exists = false
                    lastModified = Instant.EPOCH
                    size = 0
                }
            }
    }

    companion object {
        private const val DEFAULT_PART_SIZE = 5 * 1024 * 1024L
    }
}
