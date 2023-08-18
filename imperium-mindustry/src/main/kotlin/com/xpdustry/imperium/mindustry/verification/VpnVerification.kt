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
package com.xpdustry.imperium.mindustry.verification

import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.VpnAddressDetector
import com.xpdustry.imperium.mindustry.processing.Processor

class VpnVerification(private val provider: VpnAddressDetector) : Processor<VerificationContext, VerificationResult> {
    override suspend fun process(context: VerificationContext): VerificationResult {
        val result = provider.isVpnAddress(context.address)
        if (result is VpnAddressDetector.Result.Success) {
            return if (result.vpn) VerificationResult.Success else VerificationResult.Failure("VPN detected")
        }
        if (result is VpnAddressDetector.Result.Failure) {
            logger.error("Failed to verify the vpn usage for player {} ({})", context.name, context.uuid, result.exception)
        }
        return VerificationResult.Success
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
