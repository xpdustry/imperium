/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.mindustry.verification

import com.xpdustry.foundation.common.misc.LoggerDelegate
import com.xpdustry.foundation.common.misc.toValueMono
import com.xpdustry.foundation.common.network.VpnAddressDetector
import com.xpdustry.foundation.mindustry.processing.Processor
import reactor.core.publisher.Mono

class VpnVerification(private val provider: VpnAddressDetector) : Processor<VerificationContext, VerificationResult> {
    override fun process(context: VerificationContext): Mono<VerificationResult> =
        // TODO: Improve error message
        provider.isVpnAddress(context.address)
            .map { vpn ->
                if (vpn) VerificationResult.Success else VerificationResult.Failure("VPN detected")
            }
            .switchIfEmpty(VerificationResult.Success.toValueMono())
            .onErrorResume {
                logger.error("Failed to verify the vpn usage for player {} ({})", context.name, context.uuid, it)
                VerificationResult.Success.toValueMono()
            }

    companion object {
        private val logger by LoggerDelegate()
    }
}
