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
package com.xpdustry.imperium.mindustry.security

import com.google.common.net.InetAddresses
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.CoroutineHttpClient
import com.xpdustry.imperium.mindustry.processing.Processor
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import kotlin.time.Duration.Companion.seconds

private val PROVIDERS = listOf<AddressProvider>(
    AzureAddressProvider(),
    GithubActionsAddressProvider(),
    AmazonWebServicesAddressProvider(),
    GoogleCloudAddressProvider(),
    OracleCloudAddressProvider(),
)

class DdosVerification(private val http: CoroutineHttpClient) : Processor<VerificationContext, VerificationResult> {

    private val addresses: Deferred<Set<InetAddress>> = ImperiumScope.MAIN.async(start = CoroutineStart.LAZY) {
        fetchAddresses()
    }

    override suspend fun process(context: VerificationContext): VerificationResult {
        return if (addresses.await().contains(context.address)) {
            VerificationResult.Failure("You address has been marked by our anti-VPN system. Please disable it.")
        } else {
            VerificationResult.Success
        }
    }

    private suspend fun fetchAddresses(): Set<InetAddress> = withContext(ImperiumScope.IO.coroutineContext) {
        logger.info("Fetching addresses from {} cloud providers", PROVIDERS.size)
        PROVIDERS
            .map { provider ->
                async {
                    try {
                        val result = provider.fetchAddresses(http)
                        logger.debug("Found {} addresses for cloud provider '{}'", result.size, provider.name)
                        result
                    } catch (e: Exception) {
                        logger.error("Failed to fetch addresses for cloud provider '{}'", provider.name, e)
                        emptyList()
                    }
                }
            }
            .awaitAll()
            .flatMapTo(mutableSetOf()) { it }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}

private interface AddressProvider {
    val name: String
    suspend fun fetchAddresses(http: CoroutineHttpClient): List<InetAddress>
}

private abstract class JsonAddressProvider protected constructor(override val name: String) : AddressProvider {

    override suspend fun fetchAddresses(http: CoroutineHttpClient): List<InetAddress> {
        val response = http.get(fetchUri(), BodyHandlers.ofInputStream(), timeout = 10.seconds)

        if (response.statusCode() != 200) {
            throw IOException("Failed to download '$name' public addresses file (status-code: ${response.statusCode()}, uri: ${response.uri()}).")
        }

        return response.body().reader().use { reader ->
            extractAddresses(GSON.fromJson(reader, JsonObject::class.java))
        }
    }

    protected abstract suspend fun fetchUri(): URI

    protected abstract fun extractAddresses(json: JsonObject): List<InetAddress>

    companion object {
        private val GSON = Gson()
    }
}

private fun toInetAddressWithoutMask(string: String): InetAddress {
    return InetAddresses.forString(string.split("/", limit = 2)[0])
}

private class AzureAddressProvider : JsonAddressProvider("Azure") {

    // This goofy aah hacky code 💀
    override suspend fun fetchUri(): URI = withContext(ImperiumScope.IO.coroutineContext) {
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
        .flatMap { array -> array.asList().map { toInetAddressWithoutMask(it.asString) } }

    companion object {
        private const val AZURE_PUBLIC_ADDRESSES_DOWNLOAD_LINK =
            "https://www.microsoft.com/en-us/download/confirmation.aspx?id=56519"
    }
}

private class GithubActionsAddressProvider : JsonAddressProvider("Github Actions") {
    override suspend fun fetchUri(): URI = GITHUB_ACTIONS_ADDRESSES_DOWNLOAD_LINK

    override fun extractAddresses(json: JsonObject): List<InetAddress> =
        json["actions"].asJsonArray.asList().map { toInetAddressWithoutMask(it.asString) }

    companion object {
        private val GITHUB_ACTIONS_ADDRESSES_DOWNLOAD_LINK = URI("https://api.github.com/meta")
    }
}

private class AmazonWebServicesAddressProvider : JsonAddressProvider("Amazon Web Services") {
    override suspend fun fetchUri(): URI = AMAZON_WEB_SERVICES_ADDRESSES_DOWNLOAD_LINK

    override fun extractAddresses(json: JsonObject): List<InetAddress> {
        val addresses = mutableSetOf<InetAddress>()
        addresses.addAll(parsePrefix(json, "prefixes", "ip_prefix"))
        addresses.addAll(parsePrefix(json, "ipv6_prefixes", "ipv6_prefix"))
        return addresses.toList()
    }

    private fun parsePrefix(json: JsonObject, name: String, element: String): Collection<InetAddress> =
        json[name].asJsonArray.map { toInetAddressWithoutMask(it.asJsonObject[element].asString) }

    companion object {
        private val AMAZON_WEB_SERVICES_ADDRESSES_DOWNLOAD_LINK =
            URI("https://ip-ranges.amazonaws.com/ip-ranges.json")
    }
}

private class GoogleCloudAddressProvider : JsonAddressProvider("Google Cloud") {
    override suspend fun fetchUri(): URI = GOOGLE_CLOUD_ADDRESSES_DOWNLOAD_LINK

    override fun extractAddresses(json: JsonObject): List<InetAddress> =
        json["prefixes"].asJsonArray.map { extractAddress(it.asJsonObject) }

    private fun extractAddress(json: JsonObject) =
        toInetAddressWithoutMask(if (json.has("ipv4Prefix")) json["ipv4Prefix"].asString else json["ipv6Prefix"].asString)

    companion object {
        private val GOOGLE_CLOUD_ADDRESSES_DOWNLOAD_LINK =
            URI("https://www.gstatic.com/ipranges/cloud.json")
    }
}

private class OracleCloudAddressProvider : JsonAddressProvider("Oracle Cloud") {
    override suspend fun fetchUri(): URI = ORACLE_CLOUD_ADDRESSES_DOWNLOAD_LINK

    override fun extractAddresses(json: JsonObject): List<InetAddress> = json["regions"].asJsonArray
        .flatMap { it.asJsonObject["cidrs"].asJsonArray }
        .map { toInetAddressWithoutMask(it.asJsonObject["cidr"].asString) }

    companion object {
        private val ORACLE_CLOUD_ADDRESSES_DOWNLOAD_LINK =
            URI("https://docs.cloud.oracle.com/en-us/iaas/tools/public_ip_ranges.json")
    }
}
