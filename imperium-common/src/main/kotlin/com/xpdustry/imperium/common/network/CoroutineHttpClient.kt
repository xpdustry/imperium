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

import com.xpdustry.imperium.common.async.ImperiumScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.util.concurrent.Executor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

interface CoroutineHttpClient {
    suspend fun <T : Any> get(uri: URI, body: BodyHandler<T>, timeout: Duration = 5.seconds): HttpResponse<T>
}

class SimpleCoroutineHttpClient(executor: Executor) : CoroutineHttpClient {
    private val client = HttpClient.newBuilder().executor(executor).followRedirects(HttpClient.Redirect.NORMAL).build()
    override suspend fun <T : Any> get(uri: URI, body: BodyHandler<T>, timeout: Duration): HttpResponse<T> =
        withContext(ImperiumScope.IO.coroutineContext) {
            client.sendAsync(HttpRequest.newBuilder(uri).timeout(timeout.toJavaDuration()).GET().build(), body)
                .await()
        }
}
