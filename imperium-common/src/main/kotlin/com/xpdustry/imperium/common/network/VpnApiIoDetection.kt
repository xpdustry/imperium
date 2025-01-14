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
package com.xpdustry.imperium.common.network

import com.xpdustry.imperium.common.config.NetworkConfig
import java.net.InetAddress
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class VpnApiIoDetection(private val config: NetworkConfig.VpnDetectionConfig.VpnApiIo, private val http: OkHttpClient) :
    VpnDetection {
    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun isVpn(address: InetAddress): VpnDetection.Result {
        if (address.isLoopbackAddress || address.isAnyLocalAddress) {
            return VpnDetection.Result.Success(false)
        }

        val url =
            "https://vpnapi.io/api/${address.hostAddress}"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("key", config.vpnApiIoToken.value)
                .build()

        return http.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (response.code == 429) {
                return@use VpnDetection.Result.RateLimited
            }
            if (response.code != 200) {
                return@use VpnDetection.Result.Failure(
                    IllegalStateException("Unexpected status code: ${response.code}")
                )
            }
            val json = Json.decodeFromStream<JsonObject>(response.body!!.byteStream())
            VpnDetection.Result.Success(json["security"]!!.jsonObject["vpn"]!!.jsonPrimitive.boolean)
        }
    }
}
