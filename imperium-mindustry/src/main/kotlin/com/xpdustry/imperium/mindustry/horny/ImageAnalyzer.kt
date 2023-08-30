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

import com.xpdustry.imperium.common.async.ImperiumScope
import kotlinx.coroutines.withContext
import mindustry.Vars
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

interface ImageAnalyzer {
    suspend fun analyze(image: BufferedImage): Result
    enum class Result {
        NSFW, SAFE
    }
}

internal class TestImageAnalyzer : ImageAnalyzer {
    private val counter = AtomicInteger()
    override suspend fun analyze(image: BufferedImage): ImageAnalyzer.Result = withContext(ImperiumScope.IO.coroutineContext) {
        Vars.dataDirectory.child("nsfw").mkdirs()
        val output = Vars.dataDirectory.child("nsfw").child("image" + counter.getAndIncrement() + ".png").file()
        if (output.exists()) {
            output.delete()
        }
        try {
            ImageIO.write(image, "png", output)
            image.graphics.dispose()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ImageAnalyzer.Result.SAFE
    }
}
