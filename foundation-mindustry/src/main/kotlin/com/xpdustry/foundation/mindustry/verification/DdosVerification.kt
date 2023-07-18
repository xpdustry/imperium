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
package com.xpdustry.foundation.mindustry.verification

import com.google.common.net.InetAddresses
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.xpdustry.foundation.common.misc.LoggerDelegate
import com.xpdustry.foundation.common.misc.toErrorMono
import com.xpdustry.foundation.common.misc.toValueMono
import com.xpdustry.foundation.mindustry.processing.Processor
import org.jsoup.Jsoup
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.stream.Collectors

private val PROVIDERS = listOf<AddressProvider>(
    AzureAddressProvider(),
    GithubActionsAddressProvider(),
    AmazonWebServicesAddressProvider(),
    GoogleCloudAddressProvider(),
    OracleCloudAddressProvider(),
)

class DdosVerification : Processor<VerificationContext, VerificationResult> {

    private val addresses = Mono.fromRunnable<Void> {
        logger.info("Fetching addresses from {} cloud providers", PROVIDERS.size)
    }
        .thenMany(Flux.fromIterable(PROVIDERS))
        .parallel()
        .runOn(Schedulers.parallel())
        .flatMap { provider ->
            provider.fetchAddresses()
                .onErrorResume { error ->
                    logger.error("Failed to fetch addresses for cloud provider '{}'", provider.name, error)
                    emptyList<InetAddress>().toValueMono()
                }
                .doOnNext { result ->
                    logger.debug("Found {} addresses for cloud provider '{}'", result.size, provider.name)
                }
        }
        .flatMap { Flux.fromIterable(it) }
        .sequential()
        .collect(Collectors.toUnmodifiableSet())
        .cache(Duration.ofDays(1L))

    override fun process(context: VerificationContext): Mono<VerificationResult> = addresses.map {
        if (it.contains(context.address)) {
            VerificationResult.Failure("You address has been marked by our anti-VPN system. Please disable it.")
        } else {
            VerificationResult.Success
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}

private interface AddressProvider {
    val name: String
    fun fetchAddresses(): Mono<List<InetAddress>>
}

private abstract class JsonAddressProvider protected constructor(override val name: String) : AddressProvider {

    override fun fetchAddresses(): Mono<List<InetAddress>> = fetchUri()
        .map {
            HTTP.send(
                HttpRequest.newBuilder()
                    .uri(it)
                    .timeout(Duration.ofSeconds(10L))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            )
        }
        .flatMap {
            if (it.statusCode() != 200) {
                return@flatMap IOException(
                    "Failed to download '$name' public addresses file (status-code: ${it.statusCode()}, url: ${it.uri()}).",
                ).toErrorMono()
            }
            InputStreamReader(it.body(), StandardCharsets.UTF_8).use { reader ->
                extractAddresses(GSON.fromJson(reader, JsonObject::class.java)).toValueMono()
            }
        }
        .subscribeOn(Schedulers.boundedElastic())

    protected abstract fun fetchUri(): Mono<URI>

    protected abstract fun extractAddresses(json: JsonObject): List<InetAddress>

    companion object {
        private val GSON = Gson()
        private val HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5L))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
}

private fun toInetAddressWithoutMask(string: String): InetAddress {
    return InetAddresses.forString(string.split("/", limit = 2)[0])
}

private class AzureAddressProvider : JsonAddressProvider("Azure") {

    // This goofy aaah hacky code 💀
    override fun fetchUri(): Mono<URI> = Mono.fromCallable {
        Jsoup.connect(AZURE_PUBLIC_ADDRESSES_DOWNLOAD_LINK)
            .get()
            .select("a[href*=download.microsoft.com]")
            .map { element -> element.attr("abs:href") }
            .find { it.contains("ServiceTags_Public") }
            ?.let { URI(it) }
            ?: throw IOException("Failed to find Azure public addresses download link.")
    }

    override fun extractAddresses(json: JsonObject): List<InetAddress> = json["values"].asJsonArray.asList()
        .map(JsonElement::getAsJsonObject)
        .filter { it["name"].asString == "AzureCloud" }
        .map { it["properties"].asJsonObject["addressPrefixes"].asJsonArray }
        .flatMap { array ->
            array.asList().map { toInetAddressWithoutMask(it.asString) }
        }

    companion object {
        private const val AZURE_PUBLIC_ADDRESSES_DOWNLOAD_LINK =
            "https://www.microsoft.com/en-us/download/confirmation.aspx?id=56519"
    }
}

private class GithubActionsAddressProvider : JsonAddressProvider("Github Actions") {

    override fun fetchUri(): Mono<URI> = Mono.just(GITHUB_ACTIONS_ADDRESSES_DOWNLOAD_LINK)

    override fun extractAddresses(json: JsonObject): List<InetAddress> =
        json["actions"].asJsonArray.asList().map { toInetAddressWithoutMask(it.asString) }

    companion object {
        private val GITHUB_ACTIONS_ADDRESSES_DOWNLOAD_LINK =
            URI.create("https://api.github.com/meta")
    }
}

private class AmazonWebServicesAddressProvider : JsonAddressProvider("Amazon Web Services") {

    override fun fetchUri(): Mono<URI> = Mono.just(AMAZON_WEB_SERVICES_ADDRESSES_DOWNLOAD_LINK)

    override fun extractAddresses(json: JsonObject): List<InetAddress> {
        val addresses = mutableSetOf<InetAddress>()
        addresses.addAll(parsePrefix(json, "prefixes", "ip_prefix"))
        addresses.addAll(parsePrefix(json, "ipv6_prefixes", "ipv6_prefix"))
        return addresses.toList()
    }

    private fun parsePrefix(json: JsonObject, name: String, element: String): Collection<InetAddress> {
        return json[name].asJsonArray.map { toInetAddressWithoutMask(it.asJsonObject[element].asString) }
    }

    companion object {
        private val AMAZON_WEB_SERVICES_ADDRESSES_DOWNLOAD_LINK =
            URI.create("https://ip-ranges.amazonaws.com/ip-ranges.json")
    }
}

private class GoogleCloudAddressProvider : JsonAddressProvider("Google Cloud") {

    override fun fetchUri(): Mono<URI> = Mono.just(GOOGLE_CLOUD_ADDRESSES_DOWNLOAD_LINK)

    override fun extractAddresses(json: JsonObject): List<InetAddress> =
        json["prefixes"].asJsonArray.map { extractAddress(it.asJsonObject) }

    private fun extractAddress(json: JsonObject) =
        toInetAddressWithoutMask(
            if (json.has("ipv4Prefix")) json["ipv4Prefix"].asString else json["ipv6Prefix"].asString,
        )

    companion object {
        private val GOOGLE_CLOUD_ADDRESSES_DOWNLOAD_LINK =
            URI.create("https://www.gstatic.com/ipranges/cloud.json")
    }
}

private class OracleCloudAddressProvider : JsonAddressProvider("Oracle Cloud") {

    override fun fetchUri(): Mono<URI> = Mono.just(ORACLE_CLOUD_ADDRESSES_DOWNLOAD_LINK)

    override fun extractAddresses(json: JsonObject): List<InetAddress> = json["regions"].asJsonArray
        .flatMap { it.asJsonObject["cidrs"].asJsonArray }
        .map { toInetAddressWithoutMask(it.asJsonObject["cidr"].asString) }

    companion object {
        private val ORACLE_CLOUD_ADDRESSES_DOWNLOAD_LINK =
            URI.create("https://docs.cloud.oracle.com/en-us/iaas/tools/public_ip_ranges.json")
    }
}
