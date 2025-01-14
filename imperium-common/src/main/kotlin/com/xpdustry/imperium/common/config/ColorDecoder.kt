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
