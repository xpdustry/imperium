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

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.mindustry.processing.Processor

private val CRACKED_CLIENT_USERNAMES =
    setOf(
        "valve",
        "tuttop",
        "codex",
        "igggames",
        "igg-games.com",
        "igruhaorg",
        "freetp.org",
        "goldberg",
    )

private val USERNAME_BLACKLIST =
    setOf(
        "adolf hilter",
        "hitler",
        "asshole",
        "idiot",
        "nigger",
        "nigga",
        "negr",
        "neger",
        "niga",
        "stupid",
        "fuck you",
        "адольф гитлер",
        "гитлер",
        "проказник",
        "идиот",
        "ниггер",
        "нига",
        "негр",
        "негритянка",
        "негритос",
        "глупый",
        "пошел ты нахер")

private val pattern = Regex("(n|и|i|н){1,32}((g{2,32}|г{2,32}|q){1,32}|[gqг]{2,32})[e3рr]{1,32}") // Regex to check for both english and russian n-word
    
// Go figure why but some people are using cracked clients on a free game... Incredible.
class NameGatekeeper : Processor<GatekeeperContext, GatekeeperResult> {
    override suspend fun process(context: GatekeeperContext): GatekeeperResult {
        if (context.name.lowercase() in CRACKED_CLIENT_USERNAMES) {
            return GatekeeperResult.Failure(
                """
                [green]Mindustry is a free and open source game.
                [white]It is available on [royal]https://anuke.itch.io/mindustry[].
                [red]Please, get a legit copy of the game.
                """
                    .trimIndent(),
            )
        } else if (context.name.lowercase() in USERNAME_BLACKLIST || pattern.matches(context.name.lowercase())) {
            return GatekeeperResult.Failure(
                """
                Your [accent]current player-name[white] ${context.name}[white] is [#f]not allowed[white] on this server.
                Please [green[change it[white] to something else.
                """
                    .trimIndent(),
            )
        }
        return GatekeeperResult.Success
    }
}

internal class BlacklistCommand() : ImperiumApplication.Listener {
    
    @ImperiumCommand(["name-blacklist", "add"], Rank.MODERATOR)
    suspend fun onNameAddCommand(
        actor: InteractionSender.Slash,
        name: String
    ) {
        database.add(name) // this totally works legit trust
     }

    @ImperiumCommand(["name-blacklist", "remove"], Rank.MODERATOR)
    suspend fun onNameRemoveCommand(
        actor: InteractionSender.Slash,
        name: String
    ) {
        database.remove(name) // this also totally works
    }
}
// Idk i cant do anything more, so this is the last i can do, goodluck phinner!
