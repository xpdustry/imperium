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
package com.xpdustry.foundation.mindustry.core.security.verif

import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.misc.LoggerDelegate
import com.xpdustry.foundation.common.misc.toValueFlux
import com.xpdustry.foundation.common.misc.toValueMono
import com.xpdustry.foundation.mindustry.core.misc.MindustryScheduler
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.util.Priority
import mindustry.game.EventType
import mindustry.game.Team

class SimpleVerificationService : VerificationService, FoundationListener {

    private val verifications = mutableListOf<PriorityVerification>()

    override fun register(name: String, priority: Priority, verification: Verification) {
        verifications += PriorityVerification(name, priority, verification)
        verifications.sortBy { it.priority }
    }

    @EventHandler(priority = Priority.HIGH)
    fun onPlayerConnect(event: EventType.PlayerConnect) {
        val previous = event.player.team()
        event.player.team(Team.derelict)

        verifications.toValueFlux()
            .flatMap {
                it.verification.verify(event.player).onErrorResume { error ->
                    logger.error("Error while verifying player ${event.player.name()}", error)
                    Verification.Success.toValueMono()
                }
            }
            .takeUntil { it is Verification.Failure }
            .last(Verification.Success)
            .publishOn(MindustryScheduler)
            .subscribe {
                if (it is Verification.Failure) {
                    event.player.kick(it.reason, 0)
                } else {
                    event.player.team(previous)
                    event.player.sendMessage("Verification completed, welcome to Chaotic Neutral!")
                }
            }
    }

    data class PriorityVerification(val name: String, val priority: Priority, val verification: Verification)

    companion object {
        private val logger by LoggerDelegate()
    }
}
