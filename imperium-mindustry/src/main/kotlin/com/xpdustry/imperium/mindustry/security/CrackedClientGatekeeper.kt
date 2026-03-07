// SPDX-License-Identifier: GPL-3.0-only
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
