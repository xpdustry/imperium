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

import com.xpdustry.imperium.common.database.Punishment
import com.xpdustry.imperium.common.database.PunishmentManager
import com.xpdustry.imperium.mindustry.processing.Processor
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import java.time.Duration

class PunishmentGatekeeper(private val punishments: PunishmentManager) : Processor<GatekeeperContext, GatekeeperResult> {
    override suspend fun process(context: GatekeeperContext): GatekeeperResult {
        val punishment = punishments
            .findAllByTargetAddress(context.address)
            .filter { it.expired.not() }
            .toList()
            .sortedWith(Comparator.comparing(Punishment::type).thenComparing(Punishment::duration))
            .firstOrNull()

        return if (punishment == null) {
            GatekeeperResult.Success
        } else {
            GatekeeperResult.Failure(
                """
                    [red]Oh no! You are currently banned from Chaotic Neutral!
                    [accent]Reason:[white] ${punishment.reason}
                    [accent]Duration:[white] ${formatDuration(punishment.duration)}

                    [accent]Appeal in our discord server: [white]https://discord.xpdustry.com
                """.trimIndent(),
            )
        }
    }
}

private fun formatDuration(duration: Duration?): String = when {
    duration == null -> "Permanent"
    duration >= Duration.ofDays(1L) -> "${duration.toDays()} days"
    duration >= Duration.ofHours(1L) -> "${duration.toHours()} hours"
    duration >= Duration.ofMinutes(1L) -> "${duration.toMinutes()} minutes"
    else -> "${duration.seconds} seconds"
}
