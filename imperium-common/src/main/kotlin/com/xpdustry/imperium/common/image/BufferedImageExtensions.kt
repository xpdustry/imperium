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
package com.xpdustry.imperium.common.image

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

enum class ImageFormat {
    PNG,
    JPG,
}

fun BufferedImage.inputStream(format: ImageFormat = ImageFormat.PNG): InputStream {
    var image = this
    if (format == ImageFormat.JPG && this.type != BufferedImage.TYPE_INT_RGB) {
        image = BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { drawImage(this@inputStream, 0, 0, null) }.dispose()
    }
    return ByteArrayInputStream(
        ByteArrayOutputStream().also { ImageIO.write(image, format.name.lowercase(), it) }.toByteArray()
    )
}
