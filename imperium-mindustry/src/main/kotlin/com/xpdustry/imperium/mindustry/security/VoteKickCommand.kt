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

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.player.MUUID
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.MindustryUUIDAsLong
import com.xpdustry.imperium.common.misc.buildCache
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.misc.toLongMuuid
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.account.PlayerLoginEvent
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.action.Action
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.input.TextInputInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.createPlayerListTransformer
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import java.net.InetAddress
import java.util.Collections
import kotlin.math.ceil
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import mindustry.Vars
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.game.EventType.PlayerBanEvent
import mindustry.game.Team
import mindustry.gen.Player
import mindustry.net.Administration
import org.incendo.cloud.annotation.specifier.Greedy

data class VotekickEvent(val target: Player, val type: Type) {
    enum class Type {
        START,
        CLOSE,
    }
}

class VoteKickCommand(instances: InstanceManager) :
    AbstractVoteCommand<VoteKickCommand.Context>(instances.get(), "votekick", 1.minutes), ImperiumApplication.Listener {

    private val punishments = instances.get<PunishmentManager>()
    private val limiter = SimpleRateLimiter<InetAddress>(1, 60.seconds)
    private val votekickInterface = createVotekickInterface()
    private val config = instances.get<ImperiumConfig>()
    private val users = instances.get<UserManager>()
    private val marks = instances.get<MarkedPlayerManager>()
    private val recentBans =
        Collections.newSetFromMap(buildCache<MUUID, Boolean> { expireAfterWrite(1.minutes.toJavaDuration()) }.asMap())

    @EventHandler
    internal fun onPlayerBanEvent(event: PlayerBanEvent) {
        recentBans.add(MUUID.from(event.player))
    }

    @EventHandler
    internal fun onPlayerLeave(event: EventType.PlayerLeave) {
        if (MUUID.from(event.player) in recentBans) return
        manager.sessions.values
            .filter { it.objective.target == event.player }
            .forEach(VoteManager.Session<Context>::success)
    }

    @EventHandler
    internal fun onPlayerLogin(event: PlayerLoginEvent) {
        if (event.account.rank >= Rank.OVERSEER) {
            manager.sessions.values.filter { it.objective.target == event.player }.forEach { it.failure(event.player) }
        }
    }

    @ImperiumCommand(["vote", "y"])
    @ClientSide
    fun onVoteYesCommand(sender: CommandSender) {
        onPlayerVote(sender.player, getSession(sender.player.team()), Vote.YES)
    }

    @ImperiumCommand(["vote", "n"])
    @ClientSide
    fun onVoteNoCommand(sender: CommandSender) {
        onPlayerVote(sender.player, getSession(sender.player.team()), Vote.NO)
    }

    @ImperiumCommand(["vote", "c"], Rank.MODERATOR)
    @ClientSide
    fun onVoteCancelCommand(sender: CommandSender, team: Team? = null) {
        onPlayerCancel(sender.player, getSession(team ?: sender.player.team()))
    }

    @ImperiumCommand(["votekick"])
    @ClientSide
    fun onVotekickCommand(sender: CommandSender, target: Player? = null, @Greedy reason: String? = null) {
        if (!Administration.Config.enableVotekick.bool()) {
            sender.player.sendMessage("[scarlet]Vote-kick is disabled on this server.")
            return
        }
        if (target == null) {
            votekickInterface.open(sender.player)
            return
        }
        if (Entities.getPlayers().filter { !Vars.state.rules.pvp || it.team() == sender.player.team() }.size < 3) {
            sender.player.sendMessage(
                """
                [scarlet]At least 3 players are needed to start a votekick.
                Use the [orange]/report[] command instead.
                """
                    .trimIndent()
            )
            return
        }
        if (reason == null) {
            sender.player.sendMessage(
                "[orange]You need a valid reason to kick the player. Add a reason after the player name."
            )
            return
        }
        onVoteSessionStart(sender.player, getSession(target.team()), Context(target, reason, sender.player.team()))
    }

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<Context>) {
        runMindustryThread {
            Distributor.get().eventBus.post(VotekickEvent(session.objective.target, VotekickEvent.Type.CLOSE))
        }
        val yes = mutableSetOf<MindustryUUIDAsLong>()
        val nay = mutableSetOf<MindustryUUIDAsLong>()
        for ((voter, vote) in session.voters) {
            (if (vote.asBoolean()) yes else nay) += voter.toLongMuuid()
        }
        punishments.punish(
            config.server.identity,
            users.getByIdentity(session.objective.target.identity).id,
            "Votekick: ${session.objective.reason}",
            Punishment.Type.BAN,
            NetServer.kickDuration.seconds,
            Punishment.Metadata.Votekick(session.initiator!!.uuid().toLongMuuid(), yes, nay),
        )
    }

    override suspend fun onVoteSessionFailure(session: VoteManager.Session<Context>) {
        runMindustryThread {
            Distributor.get().eventBus.post(VotekickEvent(session.objective.target, VotekickEvent.Type.CLOSE))
        }
        // TODO Move to PunishmentListener
        session.objective.target.sendMessage("[sky]You are no longer involved in a vote kick, you have been unfrozen.")
    }

    override fun canParticipantStart(player: Player, objective: Context): Boolean {
        if (objective.target == player) {
            player.sendMessage("[scarlet]You can't start a votekick on yourself.")
            return false
        } else if (objective.target.team() != player.team() && Vars.state.rules.pvp) {
            player.sendMessage("[scarlet]You can't start a votekick on players from other teams.")
            return false
        } else if (objective.target.admin) {
            player.sendMessage("[scarlet]You can't start a votekick on an admin.")
            return false
        } else if (!limiter.incrementAndCheck(player.ip().toInetAddress())) {
            player.sendMessage("[scarlet]You are limited to one votekick per minute. Please try again later.")
            return false
        }
        Distributor.get().eventBus.post(VotekickEvent(objective.target, VotekickEvent.Type.START))
        return true
    }

    override fun canParticipantVote(player: Player, session: VoteManager.Session<Context>): Boolean {
        if (session.objective.target == player) {
            player.sendMessage("[scarlet]You can't vote on your own trial.")
            return false
        } else if (session.objective.team != player.team() && Vars.state.rules.pvp) {
            player.sendMessage("[scarlet]You can't vote for other teams.")
            return false
        }
        return super.canParticipantVote(player, session)
    }

    override fun getParticipants(session: VoteManager.Session<Context>): Sequence<Player> =
        super.getParticipants(session).filter { !Vars.state.rules.pvp || it.team() == session.objective.team }

    override fun getRequiredVotes(session: VoteManager.Session<Context>, players: Int): Int {
        var required =
            if (players < 4) {
                2F
            } else if (players < 5) {
                3F
            } else if (players < 21) {
                (players / 2F)
            } else {
                10F
            }
        if (marks.isMarked(session.objective.target)) {
            required /= 2F
        }
        return ceil(required).toInt()
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<Context>): String =
        "[red]VK[]: Vote started to kick ${session.objective.target.name}[] out of the server. /vote y/n in order to vote. \nReason: ${session.objective.reason}."

    private fun getSession(team: Team): VoteManager.Session<Context>? =
        manager.sessions.values.firstOrNull { it.objective.team == team }

    private fun createVotekickInterface(): Interface {
        val reasonInterface =
            TextInputInterface.create(plugin).apply {
                addTransformer { _, pane ->
                    pane.title = "Votekick (2/2)"
                    pane.description = "Enter a reason for the votekick"
                    pane.inputAction = BiAction { view, input ->
                        view.closeAll()
                        Action.command("votekick", "#" + view.state[VOTEKICK_TARGET]!!.id, input).accept(view)
                    }
                }
            }

        val playerListInterface =
            MenuInterface.create(plugin).apply {
                addTransformer { _, pane ->
                    pane.title = "Votekick (1/2)"
                    pane.content = "Select a player to votekick"
                }
                addTransformer(
                    createPlayerListTransformer { view, player ->
                        view.state[VOTEKICK_TARGET] = player
                        reasonInterface.open(view)
                    }
                )
            }

        return playerListInterface
    }

    data class Context(val target: Player, val reason: String, val team: Team)

    companion object {
        private val VOTEKICK_TARGET = stateKey<Player>("votekick_target")
    }
}
