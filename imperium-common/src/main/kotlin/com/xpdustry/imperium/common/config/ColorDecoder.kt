// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.config

import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.ThrowableFailure
import com.sksamuel.hoplite.decoder.NonNullableLeafDecoder
import com.sksamuel.hoplite.decoder.toValidated
import com.sksamuel.hoplite.fp.invalid
import java.awt.Color
import kotlin.reflect.KType

class ColorDecoder : NonNullableLeafDecoder<Color> {
    override fun supports(type: KType) = type.classifier == Color::class

    override fun safeLeafDecode(node: Node, type: KType, context: DecoderContext) =
        when (node) {
            is StringNode ->
                runCatching { Color(node.value.toInt(16)) }
                    .toValidated {
                        when (it) {
                            is NumberFormatException -> ConfigFailure.NumberConversionError(node, type)
                            else -> ThrowableFailure(it)
                        }
                    }
            else -> ConfigFailure.DecodeError(node, type).invalid()
        }
}
