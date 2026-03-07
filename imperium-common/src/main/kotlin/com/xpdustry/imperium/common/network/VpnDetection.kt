// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.network

import java.net.InetAddress

interface VpnDetection {
    suspend fun isVpn(address: InetAddress): Result

    object Noop : VpnDetection {
        override suspend fun isVpn(address: InetAddress) = Result.Success(false)
    }

    sealed interface Result {
        data class Success(val vpn: Boolean) : Result

        data class Failure(val exception: Exception) : Result

        data object RateLimited : Result
    }
}
