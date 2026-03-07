// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

// https://github.com/gildor/kotlin-coroutines-okhttp
suspend fun Call.await() = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }
        }
    )
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (_: Throwable) {}
    }
}
