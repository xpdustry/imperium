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
package com.xpdustry.foundation.mindustry.core.verification

import com.google.common.net.InetAddresses
import com.xpdustry.foundation.common.misc.LoggerDelegate
import com.xpdustry.foundation.common.misc.RateLimitException
import com.xpdustry.foundation.common.misc.toValueMono
import com.xpdustry.foundation.common.network.AddressInfoProvider
import com.xpdustry.foundation.mindustry.core.processing.Processor
import mindustry.gen.Player
import reactor.core.publisher.Mono

class VpnVerification(private val provider: AddressInfoProvider) : Processor<Player, VerificationResult> {
    override fun process(input: Player): Mono<VerificationResult> =
        provider.getInfo(InetAddresses.forString(input.con().address))
            .map {
                if (it.safe) VerificationResult.Failure("VPN detected") else VerificationResult.Success
            }
            .onErrorResume(RateLimitException::class.java) {
                VerificationResult.Success.toValueMono()
            }
            .onErrorResume {
                logger.error("Failed to verify the vpn usage for player {} ({})", input.name(), input.uuid(), it)
                VerificationResult.Success.toValueMono()
            }

    companion object {
        private val logger by LoggerDelegate()
    }
}
