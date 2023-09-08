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
package com.xpdustry.imperium.common.image

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.ImageAnnotatorSettings
import com.google.protobuf.ByteString
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.SecurityConfig
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.inputStream
import kotlin.math.roundToInt

interface ImageAnalysis {
    suspend fun isUnsafe(image: BufferedImage): Result
    object Noop : ImageAnalysis {
        override suspend fun isUnsafe(image: BufferedImage) = Result.Success(false, 0)
    }
    sealed interface Result {
        data class Success(val unsafe: Boolean, val confidence: Int) : Result
        data class Failure(val message: String) : Result
    }
}

internal class GoogleImageAnalysis(
    private val file: Path,
    private val config: SecurityConfig.ImageAnalysis.Google,
) : ImageAnalysis, ImperiumApplication.Listener {
    private lateinit var client: ImageAnnotatorClient

    override fun onImperiumInit() {
        val oldClassloader = Thread.currentThread().getContextClassLoader()
        try {
            Thread.currentThread().setContextClassLoader(GoogleImageAnalysis::class.java.getClassLoader())
            client = ImageAnnotatorClient.create(
                ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider { GoogleCredentials.fromStream(file.inputStream()) }
                    .build(),
            )
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassloader)
        }
    }

    override suspend fun isUnsafe(image: BufferedImage): ImageAnalysis.Result =
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
                return@withContext ImageAnalysis.Result.Failure("No response")
            } else if (result.responsesList.size > 1) {
                return@withContext ImageAnalysis.Result.Failure("Too many responses")
            }

            val response = result.responsesList[0]
            if (response.hasError()) {
                return@withContext ImageAnalysis.Result.Failure(response.error.message)
            }

            val max = maxOf(
                response.safeSearchAnnotation.adult.ordinal * config.adultWeight,
                response.safeSearchAnnotation.violence.ordinal * config.violenceWeight,
                response.safeSearchAnnotation.racy.ordinal * config.racyWeight,
            )

            ImageAnalysis.Result.Success(max >= 5 * config.threshold, (max * 20).roundToInt())
        }
}
