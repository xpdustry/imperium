/*
 * Imperium, the software collection powering the Xpdustry network.
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
 *
 */
package com.xpdustry.imperium.mindustry.security

import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import com.google.common.net.InetAddresses
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.await
import com.xpdustry.imperium.mindustry.processing.Processor
import java.io.IOException
import java.math.BigInteger
import java.net.Inet4Address
import java.net.URL
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
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
class DdosGatekeeper(
    private val http: OkHttpClient,
    private val config: ServerConfig.Mindustry.Security
) : Processor<GatekeeperContext, GatekeeperResult> {

    private val addresses: Deferred<RangeSet<BigInteger>> =
        ImperiumScope.MAIN.async { fetchAddressRanges() }

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
                            logger.debug(
                                "Found {} address ranges for cloud provider '{}'",
                                result.size,
                                provider.name)
                            result
                        } catch (e: Exception) {
                            logger.error(
                                "Failed to fetch address ranges for cloud provider '{}'",
                                provider.name,
                                e)
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

private abstract class JsonAddressProvider protected constructor(override val name: String) :
    AddressProvider {

    override suspend fun fetchAddressRanges(http: OkHttpClient): List<Range<BigInteger>> =
        http.newCall(Request.Builder().url(fetchUrl()).build()).await().use { response ->
            if (response.code != 200) {
                throw IOException(
                    "Failed to download '$name' public addresses file (status-code: ${response.code}, url: ${response.request.url}).")
            }
            extractAddressRanges(
                GSON.fromJson(response.body!!.charStream(), JsonObject::class.java))
        }

    protected abstract suspend fun fetchUrl(): URL

    protected abstract fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>>

    companion object {
        private val GSON = Gson()
    }
}

private object AzureAddressProvider : JsonAddressProvider("azure") {

    // This goofy aah hacky code 💀
    override suspend fun fetchUrl(): URL =
        withContext(ImperiumScope.IO.coroutineContext) {
            Jsoup.connect("https://www.microsoft.com/en-us/download/confirmation.aspx?id=56519")
                .get()
                .select("a[href*=download.microsoft.com]")
                .map { element -> element.attr("abs:href") }
                .find { it.contains("ServiceTags_Public") }
                ?.let { URL(it) }
                ?: throw IOException("Failed to find Azure public addresses download link.")
        }

    override fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>> =
        json["values"]
            .asJsonArray
            .asList()
            .map(JsonElement::getAsJsonObject)
            .filter { it["name"].asString == "AzureCloud" }
            .map { it["properties"].asJsonObject["addressPrefixes"].asJsonArray }
            .flatMap { array -> array.asList().map { createInetAddressRange(it.asString) } }
}

private object GithubActionsAddressProvider : JsonAddressProvider("github-actions") {
    override suspend fun fetchUrl(): URL = URL("https://api.github.com/meta")

    override fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>> =
        json["actions"].asJsonArray.asList().map { createInetAddressRange(it.asString) }
}

private object AmazonWebServicesAddressProvider : JsonAddressProvider("amazon-web-services") {
    override suspend fun fetchUrl(): URL = URL("https://ip-ranges.amazonaws.com/ip-ranges.json")

    override fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>> {
        val addresses = mutableSetOf<Range<BigInteger>>()
        addresses.addAll(parsePrefix(json, "prefixes", "ip_prefix"))
        addresses.addAll(parsePrefix(json, "ipv6_prefixes", "ipv6_prefix"))
        return addresses.toList()
    }

    private fun parsePrefix(
        json: JsonObject,
        name: String,
        element: String
    ): Collection<Range<BigInteger>> =
        json[name].asJsonArray.map { createInetAddressRange(it.asJsonObject[element].asString) }
}

private object GoogleCloudAddressProvider : JsonAddressProvider("google") {
    override suspend fun fetchUrl(): URL = URL("https://www.gstatic.com/ipranges/cloud.json")

    override fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>> =
        json["prefixes"].asJsonArray.map { extractAddress(it.asJsonObject) }

    private fun extractAddress(json: JsonObject) =
        createInetAddressRange(
            if (json.has("ipv4Prefix")) json["ipv4Prefix"].asString
            else json["ipv6Prefix"].asString)
}

private object OracleCloudAddressProvider : JsonAddressProvider("oracle") {
    override suspend fun fetchUrl(): URL =
        URL("https://docs.cloud.oracle.com/en-us/iaas/tools/public_ip_ranges.json")

    override fun extractAddressRanges(json: JsonObject): List<Range<BigInteger>> =
        json["regions"]
            .asJsonArray
            .flatMap { it.asJsonObject["cidrs"].asJsonArray }
            .map { createInetAddressRange(it.asJsonObject["cidr"].asString) }
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
