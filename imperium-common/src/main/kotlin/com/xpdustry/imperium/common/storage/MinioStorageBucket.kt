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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.StorageConfig
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioAsyncClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.CompletionException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class MinioStorageBucket(private val config: StorageConfig.Minio, private val http: OkHttpClient) :
    StorageBucket, ImperiumApplication.Listener {
    private lateinit var client: MinioAsyncClient

    override fun onImperiumInit() {
        client =
            MinioAsyncClient.builder()
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

    override suspend fun getObject(first: String, vararg more: String): StorageObject =
        MinioStorageObject(listOf(first, *more)).also { it.update() }

    private inner class MinioStorageObject(override val path: List<String>) : StorageObject {
        override var lastModified: Instant = Instant.EPOCH
        override var size: Long = 0
        override var exists: Boolean = false

        override suspend fun putData(stream: InputStream) =
            withContext(ImperiumScope.IO.coroutineContext) {
                client
                    .putObject(
                        PutObjectArgs.builder()
                            .bucket(config.bucket)
                            .`object`(fullPath)
                            .stream(stream, -1, DEFAULT_PART_SIZE)
                            .build()
                    )
                    .await()
                update()
            }

        override suspend fun getData(): InputStream =
            withContext(ImperiumScope.IO.coroutineContext) {
                client.getObject(GetObjectArgs.builder().bucket(config.bucket).`object`(fullPath).build()).await()
            }

        override suspend fun delete() =
            withContext(ImperiumScope.IO.coroutineContext) {
                client.removeObject(RemoveObjectArgs.builder().bucket(config.bucket).`object`(fullPath).build()).await()
                update()
            }

        suspend fun update() =
            withContext(ImperiumScope.IO.coroutineContext) {
                val stats =
                    try {
                        client
                            .statObject(StatObjectArgs.builder().bucket(config.bucket).`object`(fullPath).build())
                            .await()
                    } catch (e: CompletionException) {
                        // For some reason, the real exception is wrapped withing multiple
                        // CompletionException
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
