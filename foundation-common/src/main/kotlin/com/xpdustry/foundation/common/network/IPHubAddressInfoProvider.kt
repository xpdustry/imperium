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

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xpdustry.foundation.common.configuration.FoundationConfig
import com.xpdustry.foundation.common.misc.Country
import com.xpdustry.foundation.common.misc.RateLimitException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.kotlin.core.publisher.toMono
import java.net.InetAddress
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class IPHubAddressInfoProvider(config: FoundationConfig) : AddressInfoProvider {

    private val token: String? = config.network.ipHub?.value
    private val gson = Gson()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3L)).build()

    override fun getInfo(address: InetAddress): Mono<AddressInfo> {
        if (token == null) {
            return RateLimitException("IpHub token is blank.").toMono()
        }

        if (address.isLoopbackAddress || address.isAnyLocalAddress) {
            return AddressInfo(true, null).toMono()
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
                    return@flatMap RateLimitException().toMono()
                }

                if (it.statusCode() != 200) {
                    return@flatMap IllegalStateException("Unexpected status code: " + it.statusCode()).toMono()
                }

                // https://iphub.info/api
                // block: 0 - Residential or business IP (i.e. safe IP)
                // block: 1 - Non-residential IP (hosting provider, proxy, etc.)
                // block: 2 - Non-residential & residential IP (warning, may flag innocent people)
                val json = gson.fromJson(it.body(), JsonObject::class.java)
                return@flatMap AddressInfo(json["block"].asInt != 1, Country[json["countryCode"].asString]).toMono()
            }
    }
}
