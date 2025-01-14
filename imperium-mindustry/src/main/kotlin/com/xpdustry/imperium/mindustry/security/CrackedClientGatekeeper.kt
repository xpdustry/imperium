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

import com.xpdustry.imperium.mindustry.processing.Processor

private val CRACKED_CLIENT_USERNAMES =
    setOf("valve", "tuttop", "codex", "igggames", "igg-games.com", "igruhaorg", "freetp.org", "goldberg", "rog")

// Go figure why but some people are using cracked clients on a free game... Incredible.
class CrackedClientGatekeeper : Processor<GatekeeperContext, GatekeeperResult> {
    override suspend fun process(context: GatekeeperContext): GatekeeperResult {
        if (context.name.lowercase() in CRACKED_CLIENT_USERNAMES) {
            return GatekeeperResult.Failure(
                """
                [green]Mindustry is a free and open source game.
                [white]It is available on [royal]https://anuke.itch.io/mindustry[].
                [red]Please, get a legit copy of the game.
                """
                    .trimIndent()
            )
        }
        return GatekeeperResult.Success
    }
}
