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
import com.xpdustry.imperium.common.misc.toErrorMono
import com.xpdustry.imperium.common.misc.toValueMono
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.InetAddress
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class IpHubVpnAddressDetector(config: ImperiumConfig) : VpnAddressDetector {

    private val token: String? = (config.network.antiVpn as? NetworkConfig.AntiVPN.IpHub)?.token?.value
    private val gson = Gson()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3L)).build()

    override fun isVpnAddress(address: InetAddress): Mono<Boolean> {
        if (token == null) {
            return Mono.empty()
        }

        if (address.isLoopbackAddress || address.isAnyLocalAddress) {
            return false.toValueMono()
        }

        return Mono.fromSupplier {
            http.send(
                HttpRequest.newBuilder()
                    .uri(
                        URIBuilder("https://v2.api.iphub.info/ip/${address.hostAddress}")
                            .addParameter("key", token)
                            .build(),
                    )
                    .timeout(Duration.ofSeconds(3L))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap {
                if (it.statusCode() == 429) {
                    return@flatMap Mono.empty()
                }

                if (it.statusCode() != 200) {
                    return@flatMap IllegalStateException("Unexpected status code: " + it.statusCode()).toErrorMono()
                }

                // https://iphub.info/api
                // block: 0 - Residential or business IP (i.e. safe IP)
                // block: 1 - Non-residential IP (hosting provider, proxy, etc.)
                // block: 2 - Non-residential & residential IP (warning, may flag innocent people)
                val json = gson.fromJson(it.body(), JsonObject::class.java)
                return@flatMap (json["block"].asInt != 1).toValueMono()
            }
    }
}
