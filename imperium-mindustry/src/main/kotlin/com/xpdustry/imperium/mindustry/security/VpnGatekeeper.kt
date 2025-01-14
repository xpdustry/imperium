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

import com.xpdustry.imperium.common.misc.DISCORD_INVITATION_LINK
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.VpnDetection
import com.xpdustry.imperium.common.security.AddressWhitelist
import com.xpdustry.imperium.mindustry.processing.Processor

class VpnGatekeeper(private val provider: VpnDetection, private val whitelist: AddressWhitelist) :
    Processor<GatekeeperContext, GatekeeperResult> {
    override suspend fun process(context: GatekeeperContext): GatekeeperResult {
        if (whitelist.containsAddress(context.address)) {
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
                    Then ask for an IP unblock in the [accent]#appeals[] channel.
                    [red]Warning: During the process, only share you IP address to an admin [orange](${context.address.hostAddress})[].[]
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
