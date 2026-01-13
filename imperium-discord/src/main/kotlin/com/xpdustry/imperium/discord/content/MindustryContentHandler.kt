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
package com.xpdustry.imperium.discord.content

import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.network.await
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody

interface MindustryContentHandler {

    suspend fun parseMap(file: File): Result<ParsedMindustryMapMetadata>

    suspend fun renderMap(file: File): Result<BufferedImage>

    suspend fun parseSchematic(file: File): Result<ParsedMindustrySchematic>

    suspend fun parseSchematic(text: String): Result<ParsedMindustrySchematic>

    suspend fun renderSchematic(file: File): Result<BufferedImage>

    suspend fun renderSchematic(text: String): Result<BufferedImage>
}

data class ParsedMindustrySchematic(
    val name: String,
    val description: String,
    val labels: List<String>,
    val width: Int,
    val height: Int,
    val requirements: Map<String, Int>,
)

data class ParsedMindustryMapMetadata(
    val name: String,
    val description: String,
    val author: String,
    val width: Int,
    val height: Int,
)

class MindustryToolContentHandler(private val http: OkHttpClient) : MindustryContentHandler {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    override suspend fun parseSchematic(file: File): Result<ParsedMindustrySchematic> =
        parseSchematic(
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "schematic.msch", file.asRequestBody(MINDUSTRY_SCHEMATIC_MIME_TYPE))
                .build()
        )

    override suspend fun parseSchematic(text: String): Result<ParsedMindustrySchematic> =
        parseSchematic(MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("data", text).build())

    private suspend fun parseSchematic(body: RequestBody) = runCatching {
        http.newCall(Request.Builder().url("$BASE_URL/schematics/preview").post(body).build()).await().use { response ->
            if (response.code != 201) {
                error("Failed to parse schematic (code=${response.code}, message=${response.body.string()})")
            }
            val dto = json.decodeFromString<MindustrySchematicDTO>(response.body.string())
            ParsedMindustrySchematic(
                name = dto.name?.stripMindustryColors() ?: "Unknown",
                description = dto.description?.stripMindustryColors() ?: "",
                labels = dto.labels.map { it.stripMindustryColors() },
                width = dto.width,
                height = dto.height,
                requirements = dto.meta.requirements.associate { it.name to it.amount },
            )
        }
    }

    override suspend fun renderSchematic(text: String): Result<BufferedImage> =
        renderSchematic(MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("data", text).build())

    override suspend fun renderSchematic(file: File): Result<BufferedImage> =
        renderSchematic(
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "schematic.msch", file.asRequestBody(MINDUSTRY_SCHEMATIC_MIME_TYPE))
                .build()
        )

    private suspend fun renderSchematic(body: RequestBody) = runCatching {
        http.newCall(Request.Builder().url("$BASE_URL/schematics/image").post(body).build()).await().use { response ->
            if (response.code != 201) {
                error("Failed to preview schematic (code=${response.code}, message=${response.body.string()})")
            }
            response.body.byteStream().use(ImageIO::read)
        }
    }

    override suspend fun parseMap(file: File) = runCatching {
        http
            .newCall(
                Request.Builder()
                    .url("$BASE_URL/maps/preview")
                    .post(
                        MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("file", "map.msav", file.asRequestBody(MINDUSTRY_SAVE_MIME_TYPE))
                            .build()
                    )
                    .build()
            )
            .await()
            .use { response ->
                if (response.code != 201) {
                    error("Failed to parse schematic (code=${response.code}, message=${response.body.string()})")
                }
                val dto = json.decodeFromString<MapMetadataDTO>(response.body.string())
                ParsedMindustryMapMetadata(
                    name = dto.name?.stripMindustryColors() ?: "Unknown",
                    description = dto.description?.stripMindustryColors() ?: "",
                    author = dto.author?.stripMindustryColors() ?: "Unknown",
                    width = dto.width,
                    height = dto.height,
                )
            }
    }

    // TODO,
    //  Using BufferedImages as Return types can cause OOMs, consider switching to just returning a file,
    //  Its not like we gonna do something fancy with it.
    override suspend fun renderMap(file: File) = runCatching {
        http
            .newCall(
                Request.Builder()
                    .url("https://api.mindustry-tool.com/api/v4/maps/image")
                    .post(
                        MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("file", "map.msav", file.asRequestBody(MINDUSTRY_SAVE_MIME_TYPE))
                            .build()
                    )
                    .build()
            )
            .await()
            .use { response ->
                if (response.code != 201) {
                    error("Failed to parse schematic (code=${response.code}, message=${response.body.string()})")
                }
                response.body.byteStream().use(ImageIO::read)
            }
    }

    @Serializable
    private data class MindustrySchematicDTO(
        val name: String?,
        val description: String?,
        val tags: Map<String, String> = emptyMap(),
        val meta: MetadataDTO,
        val labels: List<String> = emptyList(),
        val width: Int,
        val height: Int,
    ) {
        @Serializable data class MetadataDTO(val requirements: List<RequirementDTO> = emptyList())

        @Serializable data class RequirementDTO(val name: String, val amount: Int)
    }

    @Serializable
    private data class MapMetadataDTO(
        val name: String?,
        val description: String?,
        val author: String?,
        val width: Int,
        val height: Int,
    )

    companion object {
        private const val BASE_URL = "https://api.mindustry-tool.com/api/v4"
        private val MINDUSTRY_SCHEMATIC_MIME_TYPE = "mindustry/msch".toMediaType()
        private val MINDUSTRY_SAVE_MIME_TYPE = "mindustry/msav".toMediaType()
    }
}
