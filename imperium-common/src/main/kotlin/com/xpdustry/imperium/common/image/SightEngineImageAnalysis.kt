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

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImageAnalysisConfig
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.await
import java.awt.image.BufferedImage
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SightEngineImageAnalysis(
    private val config: ImageAnalysisConfig.SightEngine,
    private val http: OkHttpClient,
) : ImageAnalysis, ImperiumApplication.Listener {
    private val gson = Gson()

    override suspend fun isUnsafe(image: BufferedImage): ImageAnalysis.Result {
        val bytes =
            withContext(ImperiumScope.IO.coroutineContext) {
                image.inputStream(ImageFormat.JPG).readAllBytes()
            }
        val request =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_user", config.sightEngineClient)
                .addFormDataPart("api_secret", config.sightEngineSecret.value)
                .addFormDataPart("models", "nudity-2.0,gore")
                .addFormDataPart(
                    "media",
                    "image.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaTypeOrNull(), 0, bytes.size))
                .build()

        val json =
            http
                .newCall(
                    Request.Builder()
                        .url("https://api.sightengine.com/1.0/check.json")
                        .post(request)
                        .build())
                .await()
                .use { response ->
                    gson.fromJson(response.body!!.charStream(), JsonObject::class.java)
                }

        if (json["status"].asString != "success") {
            return ImageAnalysis.Result.Failure("SightEngine API returned error: ${json["error"]}")
        }

        logger.trace("SightEngine response: {}", json)

        val explicitNudityFields = listOf("sexual_activity", "sexual_display", "sextoy", "erotica")
        val nudity =
            json["nudity"].asJsonObject.let { obj ->
                explicitNudityFields.maxOf { obj[it].asFloat }
            }
        val gore = json["gore"].asJsonObject["prob"].asFloat

        return ImageAnalysis.Result.Success(
            nudity >= config.nudityThreshold || gore >= config.goreThreshold,
            mapOf(UnsafeImageType.NUDITY to nudity, UnsafeImageType.GORE to gore))
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
