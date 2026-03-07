// SPDX-License-Identifier: GPL-3.0-only
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
