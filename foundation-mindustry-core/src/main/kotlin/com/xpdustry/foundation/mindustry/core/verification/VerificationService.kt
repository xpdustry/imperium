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

import com.google.inject.Inject
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.database.Database
import com.xpdustry.foundation.common.network.VpnAddressDetector
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.util.Priority
import mindustry.game.EventType
import mindustry.game.Team

class VerificationService @Inject constructor(
    private val pipeline: VerificationPipeline,
    private val database: Database,
    private val provider: VpnAddressDetector,
) : FoundationListener {
    override fun onFoundationInit() {
        pipeline.register("ddos", Priority.HIGH, DdosVerification())
        pipeline.register("punishment", Priority.NORMAL, PunishmentVerification(database))
        pipeline.register("vpn", Priority.LOW, VpnVerification(provider))
    }

    @EventHandler(priority = Priority.HIGH)
    fun onPlayerConnect(event: EventType.PlayerConnect) {
        val previous = event.player.team()
        event.player.team(Team.derelict)
        pipeline.build(event.player).subscribe {
            if (it is VerificationResult.Failure) {
                event.player.kick(it.reason, 0)
            } else {
                event.player.team(previous)
                event.player.sendMessage("Verification completed, welcome to Chaotic Neutral!")
            }
        }
    }
}
