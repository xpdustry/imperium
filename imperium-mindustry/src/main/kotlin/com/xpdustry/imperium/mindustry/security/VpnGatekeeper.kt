// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.imperium.common.misc.DISCORD_INVITATION_LINK
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.VpnDetection
import com.xpdustry.imperium.common.security.Whitelist
import com.xpdustry.imperium.mindustry.processing.Processor

class VpnGatekeeper(private val provider: VpnDetection, private val whitelist: Whitelist) :
    Processor<GatekeeperContext, GatekeeperResult> {
    override suspend fun process(context: GatekeeperContext): GatekeeperResult {
        if (whitelist.contains(context.address, context.uuid)) {
            return GatekeeperResult.Success
        }
        val result = provider.isVpn(context.address)
        if (result is VpnDetection.Result.Success) {
            return if (result.vpn)
                GatekeeperResult.Failure(
                    """
                    [red]VPN detected.[]
                    [lightgray]If you think this is a false positive or using a VPN is necessary to you,
                    join our discord server at [accent]${DISCORD_INVITATION_LINK}[].
                    Then ask for a VPN unblock in the [accent]#appeals[] channel.
                    [red]Warning: During the process, only share your UUID with an admin [orange](${context.uuid})[].[]
                    [grey]It is recommended to take a screenshot of this page.[]
                    """
                        .trimIndent()
                )
            else GatekeeperResult.Success
        }
        if (result is VpnDetection.Result.Failure) {
            logger.error(
                "Failed to verify the vpn usage for player {} ({})",
                context.name,
                context.uuid,
                result.exception,
            )
        }
        return GatekeeperResult.Success
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
