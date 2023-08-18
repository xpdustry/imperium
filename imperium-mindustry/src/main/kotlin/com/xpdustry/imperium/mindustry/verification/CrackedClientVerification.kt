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

import com.xpdustry.imperium.common.misc.toValueMono
import com.xpdustry.imperium.mindustry.processing.Processor
import reactor.core.publisher.Mono

private val CRACKED_CLIENT_USERNAMES = setOf(
    "valve",
    "tuttop",
    "codex",
    "igggames",
    "igg-games.com",
    "igruhaorg",
    "freetp.org",
    "goldberg",
)

// Go figure why but some people are using cracked clients on a free game... Incredible.
class CrackedClientVerification : Processor<VerificationContext, VerificationResult> {
    override fun process(context: VerificationContext): Mono<VerificationResult> {
        if (context.name.lowercase() in CRACKED_CLIENT_USERNAMES) {
            return VerificationResult.Failure(
                """
                [green]Mindustry is a free and open source game.
                [white]It is available on [royal]https://anuke.itch.io/mindustry[].
                [red]Please, get a legit copy of the game.
                """.trimIndent(),
            )
                .toValueMono()
        }
        return VerificationResult.Success.toValueMono()
    }
}