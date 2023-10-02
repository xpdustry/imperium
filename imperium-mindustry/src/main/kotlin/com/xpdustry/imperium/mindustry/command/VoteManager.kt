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
import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import fr.xpdustry.distributor.api.util.ArcCollections
import fr.xpdustry.distributor.api.util.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Groups
import mindustry.gen.Player
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

interface VoteManager<T : Any> {
    val duration: Duration
    val session: Session<T>? get() = if (sessions.size == 1) sessions.values.firstOrNull() else null
    val sessions: Map<UUID, Session<T>>

    fun start(starter: Player, vote: Boolean, target: T): Session<T>

    interface Session<T : Any> {
        val id: UUID
        val start: Instant
        val starter: Player
        val target: T
        val status: Status
        val votes: Int
        val required: Int
        val remaining: Int get() = required - votes

        fun setVote(player: Player, vote: Boolean?)
        fun getVote(player: Player): Boolean?
        fun success()
        fun failure()

        enum class Status {
            RUNNING,
            SUCCESS,
            FAILURE,
            TIMEOUT,
        }
    }
}

class SimpleVoteManager<T : Any>(
    plugin: MindustryPlugin,
    override val duration: Duration,
    private val finished: suspend (VoteManager.Session<T>) -> Unit,
    private val threshold: (Collection<Player>) -> Int = { it.size.floorDiv(2) + 1 },
    private val weight: (Player) -> Int = { 1 },
) : VoteManager<T> {
    private val _sessions = mutableMapOf<UUID, SimpleSession>()
    override val sessions: Map<UUID, VoteManager.Session<T>> = _sessions

    init {
        DistributorProvider.get().eventBus.subscribe(
            EventType.PlayerLeave::class.java,
            Priority.HIGH,
            plugin,
        ) {
            for (session in _sessions.values) {
                session.required = threshold(ArcCollections.immutableList(Groups.player))
                if (it.player.id() in session.voters) {
                    session.setVote(it.player, null)
                }
                if (it.player == session.starter) {
                    session.failure()
                }
            }
        }
    }

    override fun start(starter: Player, vote: Boolean, target: T): VoteManager.Session<T> {
        val session = SimpleSession(starter, target)
        _sessions[session.id] = session
        ImperiumScope.MAIN.launch {
            delay(duration)
            session.tryFinishWithStatus(VoteManager.Session.Status.TIMEOUT)
        }
        return session
    }

    private inner class SimpleSession(
        override val starter: Player,
        override val target: T,
    ) : VoteManager.Session<T> {
        private val voted = mutableSetOf<String>()
        private val _status = AtomicReference(VoteManager.Session.Status.RUNNING)
        val voters = mutableMapOf<Int, Boolean>()

        override val id: UUID = UUID.randomUUID()
        override val start: Instant = Instant.now()
        override val status: VoteManager.Session.Status get() = _status.get()
        override var required: Int = threshold(ArcCollections.immutableList(Groups.player))
        override val votes: Int get() = voters.entries.sumOf { (player, vote) ->
            (if (vote) 1 else -1) * weight(Groups.player.getByID(player)!!)
        }

        override fun setVote(player: Player, vote: Boolean?) {
            if (vote == null) {
                voters -= player.id()
                voted -= player.ip()
                voted -= player.uuid()
            } else {
                voters[player.id()] = vote
                voted += player.ip()
                voted += player.uuid()
            }
            if (votes >= required) {
                success()
            }
        }

        override fun getVote(player: Player): Boolean? = voters[player.id()]
        override fun success() = tryFinishWithStatus(VoteManager.Session.Status.SUCCESS)
        override fun failure() = tryFinishWithStatus(VoteManager.Session.Status.FAILURE)
        fun tryFinishWithStatus(status: VoteManager.Session.Status) {
            if (_status.compareAndSet(VoteManager.Session.Status.RUNNING, status)) {
                this@SimpleVoteManager._sessions.remove(id)
                ImperiumScope.MAIN.launch {
                    finished(this@SimpleSession)
                }
            }
        }
    }
}
