// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.content

import java.awt.image.BufferedImage
import java.io.File

interface MindustryContentHandler {

    suspend fun parseMap(file: File): Result<ParsedMindustryMapMetadata>

    suspend fun renderMap(file: File): Result<BufferedImage>
}

data class ParsedMindustryMapMetadata(
    val name: String,
    val description: String,
    val author: String,
    val width: Int,
    val height: Int,
)
