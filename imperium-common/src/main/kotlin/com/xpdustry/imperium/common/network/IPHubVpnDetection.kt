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
package com.xpdustry.imperium.common.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.NetworkConfig
import java.net.InetAddress
import java.net.http.HttpResponse

class IPHubVpnDetection(config: ImperiumConfig, private val http: CoroutineHttpClient) : VpnDetection {

    private val token: String? = (config.network.vpnDetection as? NetworkConfig.VpnDetectionConfig.IPHub)?.token?.value
    private val gson = Gson()

    override suspend fun isVpn(address: InetAddress): VpnDetection.Result {
        if (token == null) {
            return VpnDetection.Result.RateLimited
        }

        if (address.isLoopbackAddress || address.isAnyLocalAddress) {
            return VpnDetection.Result.Success(false)
        }

        val response = try {
            http.get(
                URIBuilder("https://v2.api.iphub.info/ip/${address.hostAddress}").addParameter("key", token).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        } catch (e: Exception) {
            return VpnDetection.Result.Failure(e)
        }

        if (response.statusCode() == 429) {
            return VpnDetection.Result.RateLimited
        }

        if (response.statusCode() != 200) {
            return VpnDetection.Result.Failure(
                IllegalStateException("Unexpected status code: ${response.statusCode()}"),
            )
        }

        // https://iphub.info/api
        // block: 0 - Residential or business IP (i.e. safe IP)
        // block: 1 - Non-residential IP (hosting provider, proxy, etc.)
        // block: 2 - Non-residential & residential IP (warning, may flag innocent people)
        val json = gson.fromJson(response.body(), JsonObject::class.java)
        return VpnDetection.Result.Success(json["block"].asInt != 1)
    }
}
