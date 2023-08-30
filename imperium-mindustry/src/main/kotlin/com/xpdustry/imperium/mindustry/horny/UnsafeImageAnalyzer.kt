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
package com.xpdustry.imperium.mindustry.horny

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.ImageAnnotatorSettings
import com.google.protobuf.ByteString
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.inputStream

interface UnsafeImageAnalyzer {
    suspend fun analyze(image: BufferedImage): Result
    sealed interface Result {
        data class Success(val confidence: Int) : Result
        data class Failure(val message: String) : Result
    }
}

internal object NoopUnsafeImageAnalyzer : UnsafeImageAnalyzer {
    override suspend fun analyze(image: BufferedImage) = UnsafeImageAnalyzer.Result.Success(0)
}

internal class GoogleUnsafeImageAnalyzer(private val file: Path) : UnsafeImageAnalyzer, ImperiumApplication.Listener {
    private lateinit var client: ImageAnnotatorClient

    override fun onImperiumInit() {
        val oldClassloader = Thread.currentThread().getContextClassLoader()
        try {
            Thread.currentThread().setContextClassLoader(GoogleUnsafeImageAnalyzer::class.java.getClassLoader())
            client = ImageAnnotatorClient.create(
                ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider { GoogleCredentials.fromStream(file.inputStream()) }
                    .build(),
            )
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassloader)
        }
    }

    override suspend fun analyze(image: BufferedImage): UnsafeImageAnalyzer.Result =
        withContext(ImperiumScope.IO.coroutineContext) {
            val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }
                .let { ByteString.copyFrom(it.toByteArray()) }
            val result = client.batchAnnotateImages(
                listOf(
                    AnnotateImageRequest.newBuilder()
                        .addFeatures(Feature.newBuilder().setType(Feature.Type.SAFE_SEARCH_DETECTION).build())
                        .setImage(Image.newBuilder().setContent(bytes).build())
                        .build(),
                ),
            )

            if (result.responsesList.isEmpty()) {
                return@withContext UnsafeImageAnalyzer.Result.Failure("No response")
            } else if (result.responsesList.size > 1) {
                return@withContext UnsafeImageAnalyzer.Result.Failure("Too many responses")
            }

            val response = result.responsesList[0]
            if (response.hasError()) {
                return@withContext UnsafeImageAnalyzer.Result.Failure(response.error.message)
            }

            // Values are on a scale of 5, so we multiply them by a given weight around 20
            val adult = response.safeSearchAnnotation.adult.ordinal * 20
            val violence = response.safeSearchAnnotation.violence.ordinal * 20
            val racy = response.safeSearchAnnotation.racy.ordinal * 15 // Racy is the horny stuff but not explicit
            UnsafeImageAnalyzer.Result.Success(maxOf(adult, violence, racy))
        }
}
