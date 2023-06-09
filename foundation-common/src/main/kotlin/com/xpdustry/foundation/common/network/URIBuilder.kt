/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.common.network

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class URIBuilder(private val base: String) {
    private val parameters: MutableMap<String, String> = HashMap()

    fun addParameter(parameter: String, value: String): URIBuilder {
        parameters[parameter] = value
        return this
    }

    fun build(): URI {
        val builder = StringBuilder(base.length * 2).append(base)
        var first = true
        for ((key, value) in parameters) {
            builder.append(if (first) '?' else '&')
            first = false
            builder.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8))
        }
        return URI.create(builder.toString())
    }
}
