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
package com.xpdustry.imperium.mindustry.command

import com.xpdustry.imperium.common.async.ImperiumScope
import fr.xpdustry.distributor.api.util.ArcCollections
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mindustry.gen.Groups
import mindustry.gen.Player
import kotlin.math.max
import kotlin.time.Duration

// TODO Replace individual callbacks by a proper listener ?
class VoteManager<T : Any>(
    private val duration: Duration,
    private val success: suspend (T) -> Unit,
    private val failure: suspend (T) -> Unit,
    private val _requiredVotes: (Collection<Player>) -> Int = { max(2, it.size / 2) },
) {
    var current: Session? = null
        private set
    private var currentTimer: Job? = null

    fun start(target: T) {
        if (current != null) {
            return
        }
        current = Session(target)
        currentTimer = ImperiumScope.MAIN.launch {
            delay(duration)
            failure(target)
        }
    }

    fun cancel() {
        currentTimer?.cancel()
        currentTimer = null
        current = null
    }

    inner class Session(val target: T) {
        val requiredVotes: Int get() = _requiredVotes(ArcCollections.immutableList(Groups.player))
        val remainingVotes: Int get() = max(0, requiredVotes - votes)
        var votes = 0
            private set

        private val voted = mutableSetOf<String>()

        fun canVote(player: Player): Boolean {
            return player.uuid() !in voted && player.ip() !in voted
        }

        fun vote(player: Player, value: Int) {
            if (!canVote(player)) {
                return
            }

            votes += value
            voted += player.uuid()
            voted += player.ip()

            if (remainingVotes == 0) {
                ImperiumScope.MAIN.launch {
                    cancel()
                    success(target)
                }
            }
        }
    }
}
