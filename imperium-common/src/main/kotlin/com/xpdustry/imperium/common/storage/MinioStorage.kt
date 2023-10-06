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
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.StorageConfig
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.ListObjectsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioAsyncClient
import io.minio.PutObjectArgs
import io.minio.RemoveBucketArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import io.minio.http.Method
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

class MinioStorage(private val config: ImperiumConfig, private val http: OkHttpClient) : Storage, ImperiumApplication.Listener {
    private lateinit var client: MinioAsyncClient

    override fun onImperiumInit() {
        if (config.storage !is StorageConfig.Minio) {
            throw IllegalStateException("The current storage configuration is not Minio")
        }
        client = with(config.storage) {
            MinioAsyncClient.builder()
                .endpoint(host, port, secure)
                .credentials(accessKey.value, secretKey.value)
                .httpClient(http)
                .build()
        }
        try {
            client.listBuckets().get(10L, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw RuntimeException("Could not connect to Minio", e)
        }
    }

    override suspend fun getBucket(name: String, create: Boolean): Bucket? {
        if (client.bucketExists(BucketExistsArgs.builder().bucket(name).build()).await()) {
            return MinioBucket(name)
        }
        if (create) {
            client.makeBucket(MakeBucketArgs.builder().bucket(name).build()).await()
            return MinioBucket(name)
        }
        return null
    }

    override suspend fun listBuckets(): List<Bucket> =
        client.listBuckets().await().map { MinioBucket(it.name()) }

    override suspend fun deleteBucket(name: String) {
        client.removeBucket(RemoveBucketArgs.builder().bucket(name).build()).await()
    }

    private inner class MinioBucket(override val name: String) : Bucket {
        override suspend fun getObject(name: String): S3Object? = try {
            val stats = client.statObject(
                StatObjectArgs.builder()
                    .bucket(this.name)
                    .`object`(name)
                    .build(),
            ).await()
            MinioObject(this.name, name.split("/"), stats.size(), stats.lastModified().toInstant())
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
        } catch (e: Exception) {
            throw e
        }

        override suspend fun putObject(name: String, stream: InputStream) {
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(this.name)
                    .`object`(name)
                    .stream(stream, -1, DEFAULT_PART_SIZE)
                    .build(),
            ).await()
        }

        // TODO: Objects are fetched lazily by the first call, use more appropriate result type
        override suspend fun listObjects(prefix: String, recursive: Boolean): List<S3Object> = withContext(ImperiumScope.IO.coroutineContext) {
            client.listObjects(
                ListObjectsArgs.builder()
                    .bucket(name)
                    .prefix(prefix)
                    .recursive(recursive)
                    .build(),
            )
                .map { it.get() }
                .filter { !it.isDir && !it.isDeleteMarker }
                .map { MinioObject(name, it.objectName().split("/"), it.size(), it.lastModified().toInstant()) }
        }

        override suspend fun deleteObject(name: String) {
            client.removeObject(RemoveObjectArgs.builder().bucket(this.name).`object`(name).build()).await()
        }
    }

    private inner class MinioObject(
        private val bucket: String,
        override val path: List<String>,
        override val size: Long,
        override val lastModified: Instant,
    ) : S3Object {
        override suspend fun getStream(): InputStream =
            client.getObject(GetObjectArgs.builder().bucket(bucket).`object`(path.joinToString("/")).build()).await()

        override suspend fun getDownloadUrl(expiration: kotlin.time.Duration): URI =
            withContext(ImperiumScope.IO.coroutineContext) {
                URI(
                    client.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .`object`(path.joinToString("/"))
                            .expiry(expiration.inWholeSeconds.toInt())
                            .build(),
                    ),
                )
            }
    }

    companion object {
        private const val DEFAULT_PART_SIZE = 5 * 1024 * 1024L
    }
}
