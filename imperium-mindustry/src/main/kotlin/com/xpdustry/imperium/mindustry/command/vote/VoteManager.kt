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
package com.xpdustry.imperium.mindustry.command.vote

import com.xpdustry.imperium.common.misc.MindustryUUID
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import mindustry.gen.Player

interface VoteManager<O> {
    val sessions: Map<UUID, Session<O>>
    val session: Session<O>?
        get() = if (sessions.size == 1) sessions.values.firstOrNull() else null

    fun start(objective: O): Session<O>

    fun start(objective: O, initiator: Player, vote: Vote): Session<O>

    interface Session<T> {
        val id: UUID
        val duration: Duration
        val start: Instant
        val status: Status
        val initiator: Player?
        val canceller: Player?
        val objective: T
        val votes: Int
        val required: Int
        val voters: Map<MindustryUUID, Vote>

        fun getVote(player: Player): Vote?

        fun setVote(player: Player, vote: Vote)

        fun success()

        fun failure(canceller: Player? = null)
    }

    interface Listener<O> {
        fun onVoteStart(session: Session<O>)

        fun onVoterVote(session: Session<O>, player: Player, vote: Vote)

        fun onVoteClose(session: Session<O>)
    }

    enum class Status {
        RUNNING,
        SUCCESS,
        FAILURE,
    }
}
