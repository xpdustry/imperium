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

import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.snowflake.timestamp
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.mindustry.processing.Processor

// TODO Improve ban message
class PunishmentGatekeeper(
    private val punishments: PunishmentManager,
    private val renderer: TimeRenderer
) : Processor<GatekeeperContext, GatekeeperResult> {
    override suspend fun process(context: GatekeeperContext): GatekeeperResult {
        val punishment =
            punishments
                .findAllByIdentity(
                    Identity.Mindustry("unknown", context.uuid, context.usid, context.address))
                .filter { !it.expired && it.type == Punishment.Type.BAN }
                .toList()
                .maxByOrNull { it.snowflake.timestamp }

        return if (punishment == null) {
            GatekeeperResult.Success
        } else {
            GatekeeperResult.Failure(
                """
                [red]Oh no! You are currently banned from Chaotic Neutral!
                [accent]Reason:[white] ${punishment.reason}
                [accent]Duration:[white] ${renderer.renderDuration(punishment.duration)}
                [accent]Expires:[white] ${punishment.expiration}
                [accent]Punishment id:[white] ${punishment.snowflake}

                [accent]Appeal in our discord server: [white]https://discord.xpdustry.com
                """
                    .trimIndent(),
            )
        }
    }
}
