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
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.StorageConfig
import com.xpdustry.imperium.common.misc.toValueFlux
import com.xpdustry.imperium.common.misc.toValueMono
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
import io.minio.http.HttpUtils
import io.minio.http.Method
import okhttp3.OkHttpClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.InputStream
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class MinioStorage(private val config: ImperiumConfig) : Storage, ImperiumApplication.Listener {
    private lateinit var client: MinioAsyncClient
    private lateinit var httpClient: OkHttpClient

    override fun onImperiumInit() {
        if (config.storage !is StorageConfig.Minio) {
            throw IllegalStateException("The current storage configuration is not Minio")
        }
        // TODO Replace java HttpClient with OkHttp everywhere?
        val timeout = Duration.ofMinutes(5).toMillis()
        httpClient = HttpUtils.newDefaultHttpClient(timeout, timeout, timeout)
        client = with(config.storage) {
            MinioAsyncClient.builder()
                .endpoint(host, port, secure)
                .credentials(accessKey.value, secretKey.value)
                .httpClient(httpClient)
                .build()
        }

        try {
            client.listBuckets().join()
        } catch (e: Exception) {
            throw RuntimeException("Could not connect to Minio", e)
        }
    }

    override fun onImperiumExit() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        httpClient.cache?.close()
        httpClient.dispatcher.cancelAll()
        httpClient.dispatcher.executorService.awaitTermination(5, TimeUnit.SECONDS)
    }

    override fun getBucket(name: String, create: Boolean): Mono<Bucket> = Mono.defer {
        client.bucketExists(BucketExistsArgs.builder().bucket(name).build()).toValueMono().flatMap { exists ->
            if (exists) {
                MinioBucket(name).toValueMono()
            } else if (create) {
                return@flatMap client.makeBucket(MakeBucketArgs.builder().bucket(name).build())
                    .toValueMono()
                    .thenReturn(MinioBucket(name))
            } else {
                Mono.empty()
            }
        }
    }

    override fun listBuckets(): Flux<Bucket> = Flux.defer {
        client.listBuckets().toValueMono().flatMapMany { buckets ->
            Flux.fromIterable(buckets).map { MinioBucket(it.name()) }
        }
    }

    override fun deleteBucket(name: String): Mono<Void> = Mono.defer {
        client.removeBucket(RemoveBucketArgs.builder().bucket(name).build()).toValueMono().then()
    }

    private inner class MinioBucket(override val name: String) : Bucket {
        override fun getObject(name: String): Mono<S3Object> = Mono.defer {
            client.statObject(
                StatObjectArgs.builder()
                    .bucket(this.name)
                    .`object`(name)
                    .build(),
            ).toValueMono()
                .map { MinioObject(this.name, name.split("/"), it.size(), it.lastModified().toInstant()) }
                .onErrorResume {
                    if (it is ErrorResponseException && it.errorResponse().code() == "NoSuchKey") {
                        Mono.empty()
                    } else {
                        Mono.error(it)
                    }
                }
        }

        override fun putObject(name: String, stream: InputStream) = Mono.defer {
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(this.name)
                    .`object`(name)
                    .stream(stream, -1, DEFAULT_PART_SIZE)
                    .build(),
            ).toValueMono().then()
        }

        override fun listObjects(prefix: String, recursive: Boolean) = Flux.defer<S3Object> {
            client.listObjects(
                ListObjectsArgs.builder()
                    .bucket(this.name)
                    .prefix(prefix)
                    .recursive(recursive)
                    .build(),
            ).toValueFlux()
                .map { it.get() }
                .filter { !it.isDir && !it.isDeleteMarker }
                .map { MinioObject(this.name, it.objectName().split("/"), it.size(), it.lastModified().toInstant()) }
        }

        override fun deleteObject(name: String): Mono<Void> = Mono.defer {
            client.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(this.name)
                    .`object`(name)
                    .build(),
            ).toValueMono().then()
        }
    }

    private inner class MinioObject(
        private val bucket: String,
        override val path: List<String>,
        override val size: Long,
        override val lastModified: Instant,
    ) : S3Object {
        override fun getStream(): Mono<InputStream> = Mono.usingWhen(
            Mono.defer {
                client.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucket)
                        .`object`(path.joinToString("/"))
                        .build(),
                ).toValueMono()
            },
            { it.toValueMono() },
            {
                Mono.fromRunnable<InputStream> { it.close() }
                    .subscribeOn(Schedulers.boundedElastic())
            },
        )

        override fun getDownloadUrl(expiration: Duration): Mono<URL> = Mono.defer {
            client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .`object`(path.joinToString("/"))
                    .expiry(expiration.toSeconds().toInt())
                    .build(),
            ).toValueMono().map(::URL)
        }
    }

    companion object {
        private const val DEFAULT_PART_SIZE = 5 * 1024 * 1024L
    }
}
