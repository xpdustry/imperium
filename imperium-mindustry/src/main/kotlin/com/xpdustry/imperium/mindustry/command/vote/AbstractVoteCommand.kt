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

import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.joinTime
import kotlin.time.Duration
import kotlinx.coroutines.launch
import mindustry.gen.Player

abstract class AbstractVoteCommand<O>(
    protected val plugin: MindustryPlugin,
    private val name: String,
    duration: Duration,
) {
    protected val manager: VoteManager<O> =
        SimpleVoteManager(
            plugin = plugin,
            required = { getRequiredVotes(it, getParticipants(it).count()) },
            duration = duration,
            finished = ::onVoteSessionClose,
        )

    protected fun onVoteSessionStart(player: Player, session: VoteManager.Session<O>?, objective: O) {
        if (session != null) {
            player.sendMessage("[scarlet]There is already a vote for [orange]'$name'[] in progress!")
            return
        }
        if (canParticipantStart(player, objective)) {
            val newSession = manager.start(objective, player, Vote.YES)
            if (newSession.required == 1) {
                newSession.success()
                return
            }
            val message =
                "[orange]A vote for [yellow]'$name'[] has been started by ${player.name}[orange]. [yellow]${newSession.required}[] vote(s) are required. [lightgray]${getVoteSessionDetails(newSession)}"
            getParticipants(newSession).forEach { it.sendMessage(message) }
        }
    }

    protected fun onPlayerVote(player: Player, session: VoteManager.Session<O>?, vote: Vote) {
        if (session == null) {
            player.sendMessage("[scarlet]No [orange]'$name'[] vote is running.")
            return
        }
        if (canParticipantVote(player, session)) {
            val message =
                "[orange]${player.plainName()}[green] has voted [accent]${vote.name.lowercase()}[] for the '$name' vote. (${session.votes + vote.asInt()}/${session.required})."
            getParticipants(session).forEach { it.sendMessage(message) }
            session.setVote(player, vote)
        }
    }

    protected fun onPlayerCancel(player: Player, session: VoteManager.Session<O>?) {
        if (session == null) {
            player.sendMessage("[scarlet]There is no vote for [orange]'$name'[] in progress!")
        } else {
            session.failure(player)
        }
    }

    protected open fun onPlayerForceSuccess(player: Player, session: VoteManager.Session<O>?) {
        if (session == null) {
            player.sendMessage("[scarlet]There is no vote for [orange]'$name'[] in progress!")
        } else {
            session.success()
        }
    }

    protected abstract suspend fun onVoteSessionSuccess(session: VoteManager.Session<O>)

    protected open suspend fun onVoteSessionFailure(session: VoteManager.Session<O>) = Unit

    protected abstract fun getVoteSessionDetails(session: VoteManager.Session<O>): String

    protected open fun canParticipantVote(player: Player, session: VoteManager.Session<O>): Boolean {
        if (session.getVote(player) != null) {
            player.sendMessage("[scarlet]You have already voted.")
            return false
        }
        if (session.start < player.joinTime) {
            player.sendMessage("[scarlet]You can't participate in this vote.")
            return false
        }
        return true
    }

    protected open fun canParticipantStart(player: Player, objective: O): Boolean = true

    protected open fun getParticipants(session: VoteManager.Session<O>): Sequence<Player> =
        Entities.getPlayers().asSequence()

    protected open fun getRequiredVotes(session: VoteManager.Session<O>, players: Int): Int = (players / 2) + 1

    private fun onVoteSessionClose(session: VoteManager.Session<O>) {
        when (session.status) {
            VoteManager.Status.FAILURE -> {
                val message =
                    if (session.canceller != null) {
                        "[scarlet]The [orange]'$name'[] vote has been cancelled by ${session.canceller!!.name}."
                    } else {
                        "[scarlet]The [orange]'$name'[] vote has failed."
                    }
                getParticipants(session).forEach { it.sendMessage(message) }
                ImperiumScope.MAIN.launch { onVoteSessionFailure(session) }
            }
            VoteManager.Status.SUCCESS -> {
                val message = "[green]The [orange]'$name'[] vote has succeeded."
                getParticipants(session).forEach { it.sendMessage(message) }
                ImperiumScope.MAIN.launch { onVoteSessionSuccess(session) }
            }
            VoteManager.Status.RUNNING -> {
                error("Vote session is still running.")
            }
        }
    }
}
