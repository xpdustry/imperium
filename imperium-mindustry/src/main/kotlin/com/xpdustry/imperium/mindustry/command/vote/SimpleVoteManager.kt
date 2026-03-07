// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.command.vote

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.misc.MindustryUUID
import java.time.Instant
import java.util.Objects
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Player

internal class SimpleVoteManager<O>(
    plugin: MindustryPlugin,
    private val required: (VoteManager.Session<O>) -> Int,
    private val duration: Duration,
    private val finished: (VoteManager.Session<O>) -> Unit,
) : VoteManager<O> {
    private val _sessions = ConcurrentHashMap<UUID, SimpleSession>()
    override val sessions: Map<UUID, VoteManager.Session<O>> = _sessions

    init {
        Distributor.get().eventBus.subscribe(EventType.PlayerLeave::class.java, Priority.HIGH, plugin) {
            for (session in _sessions.values) {
                session.required = required(session)
            }
        }

        Distributor.get().eventBus.subscribe(EventType.PlayerJoin::class.java, Priority.HIGH, plugin) {
            for (session in _sessions.values) {
                session.required = required(session)
            }
        }
    }

    override fun start(objective: O): VoteManager.Session<O> {
        val session = SimpleSession(objective, null)
        _sessions[session.id] = session
        ImperiumScope.MAIN.launch {
            delay(duration)
            session.tryFinishWithStatus(VoteManager.Status.FAILURE)
        }
        return session
    }

    override fun start(objective: O, initiator: Player, vote: Vote): VoteManager.Session<O> {
        val session = SimpleSession(objective, initiator)
        _sessions[session.id] = session
        ImperiumScope.MAIN.launch {
            delay(duration)
            session.tryFinishWithStatus(VoteManager.Status.FAILURE)
        }
        session.setVote0(initiator, vote)
        return session
    }

    private inner class SimpleSession(override val objective: O, override val initiator: Player?) :
        VoteManager.Session<O> {
        override val duration: Duration
            get() = this@SimpleVoteManager.duration

        private val _status = AtomicReference(VoteManager.Status.RUNNING)
        override val status: VoteManager.Status
            get() = _status.get()

        override var canceller: Player? = null
        override val id: UUID = UUID.randomUUID()
        override val start: Instant = Instant.now()
        override var required: Int = required(this)
        override val votes: Int
            get() = voters.values.sumOf(Vote::asInt)

        private val _voters = mutableMapOf<VoteKey, Vote>()
        override val voters: Map<MindustryUUID, Vote>
            get() = _voters.mapKeys { it.key.uuid }

        override fun setVote(player: Player, vote: Vote) {
            setVote0(player, vote)
        }

        fun setVote0(player: Player, vote: Vote?) {
            if (vote == null) {
                _voters.remove(player.key)
            } else {
                _voters[player.key] = vote
            }
            if (votes >= required) {
                success()
            }
        }

        override fun getVote(player: Player): Vote? = _voters[player.key]

        override fun success() = tryFinishWithStatus(VoteManager.Status.SUCCESS)

        override fun failure(canceller: Player?) =
            tryFinishWithStatus(VoteManager.Status.FAILURE) { this.canceller = canceller }

        fun tryFinishWithStatus(status: VoteManager.Status, callback: () -> Unit = {}) {
            if (_status.compareAndSet(VoteManager.Status.RUNNING, status)) {
                this@SimpleVoteManager._sessions.remove(id)
                callback()
                finished(this)
            }
        }

        private val Player.key: VoteKey
            get() = VoteKey(ip(), uuid())
    }

    @Suppress("EqualsOrHashCode")
    data class VoteKey(val ip: String, val uuid: MindustryUUID) {
        override fun hashCode(): Int = Objects.hash(ip)
    }
}
