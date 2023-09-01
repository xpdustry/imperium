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

import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.VpnAddressDetector
import com.xpdustry.imperium.mindustry.processing.Processor

class VpnGatekeeper(private val provider: VpnAddressDetector) : Processor<GatekeeperContext, GatekeeperResult> {
    override suspend fun process(context: GatekeeperContext): GatekeeperResult {
        val result = provider.isVpnAddress(context.address)
        if (result is VpnAddressDetector.Result.Success) {
            return if (result.vpn) GatekeeperResult.Success else GatekeeperResult.Failure("VPN detected")
        }
        if (result is VpnAddressDetector.Result.Failure) {
            logger.error("Failed to verify the vpn usage for player {} ({})", context.name, context.uuid, result.exception)
        }
        return GatekeeperResult.Success
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
