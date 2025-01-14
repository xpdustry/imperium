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
package com.xpdustry.imperium.mindustry.security

import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import com.google.common.net.InetAddresses
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.MindustryConfig
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.await
import com.xpdustry.imperium.mindustry.processing.Processor
import java.io.IOException
import java.math.BigInteger
import java.net.Inet4Address
import java.net.URI
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

private val PROVIDERS =
    listOf<AddressProvider>(
        AzureAddressProvider,
        GithubActionsAddressProvider,
        AmazonWebServicesAddressProvider,
        GoogleCloudAddressProvider,
        OracleCloudAddressProvider,
    )

// TODO
//   shit is so efficient I am not even aware of the attacks,
//   although I do want to have a notification
class DdosGatekeeper(private val http: OkHttpClient, private val config: MindustryConfig.Security) :
    Processor<GatekeeperContext, GatekeeperResult> {

    private val addresses: Deferred<RangeSet<BigInteger>> = ImperiumScope.MAIN.async { fetchAddressRanges() }

    override suspend fun process(context: GatekeeperContext): GatekeeperResult {
        return if (addresses.await().contains(BigInteger(1, context.address.address))) {
            GatekeeperResult.Failure("DDOS attack detected. Go fuck yourself.", silent = true)
        } else {
            GatekeeperResult.Success
        }
    }

    private suspend fun fetchAddressRanges(): RangeSet<BigInteger> =
        withContext(ImperiumScope.IO.coroutineContext) {
            if (!config.gatekeeper) return@withContext TreeRangeSet.create()
            logger.info("Fetching addresses from {} cloud providers", PROVIDERS.size)
            PROVIDERS.map { provider ->
                    async {
                        try {
                            val result = provider.fetchAddressRanges(http)
                            logger.debug("Found {} address ranges for cloud provider '{}'", result.size, provider.name)
                            result
                        } catch (e: Exception) {
                            logger.error("Failed to fetch address ranges for cloud provider '{}'", provider.name, e)
                            emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .fold(TreeRangeSet.create()) { set, range ->
                    set.add(range)
                    set
                }
        }

    companion object {
        private val logger by LoggerDelegate()
    }
}

private interface AddressProvider {
    val name: String

    suspend fun fetchAddressRanges(http: OkHttpClient): List<Range<BigInteger>>
}

private abstract class JsonAddressProvider protected constructor(override val name: String) : AddressProvider {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun fetchAddressRanges(http: OkHttpClient): List<Range<BigInteger>> =
        http.newCall(Request.Builder().url(fetchUri().toURL()).build()).await().use { response ->
            if (response.code != 200) {
                throw IOException(
                    "Failed to download '$name' public addresses file (status-code: ${response.code}, url: ${response.request.url})."
                )
            }
            extractAddressRanges(Json.decodeFromStream<JsonObject>(response.body!!.byteStream()))
        }

    protected abstract suspend fun fetchUri(): URI

    protected abstract fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>>
}

private object AzureAddressProvider : JsonAddressProvider("azure") {

    // This goofy aah hacky code 💀
    override suspend fun fetchUri() =
        withContext(ImperiumScope.IO.coroutineContext) {
            Jsoup.connect("https://www.microsoft.com/en-us/download/details.aspx?id=56519")
                .get()
                .select("a[href*=download.microsoft.com]")
                .map { element -> element.attr("abs:href") }
                .find { it.contains("ServiceTags_Public") }
                ?.let(::URI) ?: throw IOException("Failed to find Azure public addresses download link.")
        }

    override fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>> =
        json["values"]!!
            .jsonArray
            .map(JsonElement::jsonObject)
            .filter { it["name"]!!.jsonPrimitive.content == "AzureCloud" }
            .map { it["properties"]!!.jsonObject["addressPrefixes"]!!.jsonArray }
            .flatMap { array -> array.map { createInetAddressRange(it.jsonPrimitive.content) } }
}

private object GithubActionsAddressProvider : JsonAddressProvider("github-actions") {
    override suspend fun fetchUri() = URI("https://api.github.com/meta")

    override fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>> =
        json["actions"]!!.jsonArray.map { createInetAddressRange(it.jsonPrimitive.content) }
}

private object AmazonWebServicesAddressProvider : JsonAddressProvider("amazon-web-services") {
    override suspend fun fetchUri() = URI("https://ip-ranges.amazonaws.com/ip-ranges.json")

    override fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>> {
        val addresses = mutableSetOf<Range<BigInteger>>()
        addresses.addAll(parsePrefix(json, "prefixes", "ip_prefix"))
        addresses.addAll(parsePrefix(json, "ipv6_prefixes", "ipv6_prefix"))
        return addresses.toList()
    }

    private fun parsePrefix(json: JsonObject, name: String, element: String): Collection<Range<BigInteger>> =
        json[name]!!.jsonArray.map { createInetAddressRange(it.jsonObject[element]!!.jsonPrimitive.content) }
}

private object GoogleCloudAddressProvider : JsonAddressProvider("google") {
    override suspend fun fetchUri() = URI("https://www.gstatic.com/ipranges/cloud.json")

    override fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>> =
        json["prefixes"]!!.jsonArray.map { extractAddress(it.jsonObject) }

    private fun extractAddress(json: JsonObject) =
        createInetAddressRange((json["ipv4Prefix"] ?: json["ipv6Prefix"])!!.jsonPrimitive.content)
}

private object OracleCloudAddressProvider : JsonAddressProvider("oracle") {
    override suspend fun fetchUri() = URI("https://docs.cloud.oracle.com/en-us/iaas/tools/public_ip_ranges.json")

    override fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>> =
        json["regions"]!!
            .jsonArray
            .flatMap { it.jsonObject["cidrs"]!!.jsonArray }
            .map { createInetAddressRange(it.jsonObject["cidr"]!!.jsonPrimitive.content) }
}

private fun createInetAddressRange(address: String): Range<BigInteger> {
    val parts = address.split("/", limit = 2)
    val parsedAddress = InetAddresses.forString(parts[0])
    if (parts.size != 2) {
        return Range.singleton(BigInteger(1, parsedAddress.address))
    }
    val bigIntAddress = BigInteger(1, parsedAddress.address)
    val cidrPrefixLen = parts[1].toInt()
    val bits = if (parsedAddress is Inet4Address) 32 else 128
    val addressCount = BigInteger.ONE.shiftLeft(bits - cidrPrefixLen)
    return Range.closed(bigIntAddress, bigIntAddress + addressCount)
}
