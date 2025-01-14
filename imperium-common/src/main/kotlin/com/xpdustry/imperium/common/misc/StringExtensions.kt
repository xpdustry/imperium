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
package com.xpdustry.imperium.common.misc

import com.google.common.net.InetAddresses
import java.awt.Color
import java.net.InetAddress
import java.util.Base64
import java.util.Locale

fun String.capitalize(locale: Locale = Locale.ROOT, all: Boolean = false): String =
    if (all) split(" ").joinToString(" ") { it.capitalize(locale) }
    else if (isBlank()) this else get(0).uppercase(locale) + substring(1)

fun Color.toHexString(): String =
    if (alpha == 255) String.format("#%02x%02x%02x", red, green, blue)
    else String.format("#%02x%02x%02x%02x", alpha, red, green, blue)

fun String.toInetAddress(): InetAddress = InetAddresses.forString(this)

fun String.toInetAddressOrNull(): InetAddress? = if (InetAddresses.isInetAddress(this)) toInetAddress() else null

fun ByteArray.encodeBase64(): String = Base64.getEncoder().encodeToString(this)

fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)

private val LINK_REGEX = Regex("(https?://|discord.gg)")

fun String.containsLink(): Boolean = LINK_REGEX.containsMatchIn(lowercase())
