// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.command.vote

import com.xpdustry.imperium.common.misc.MindustryUUID
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Instant
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
