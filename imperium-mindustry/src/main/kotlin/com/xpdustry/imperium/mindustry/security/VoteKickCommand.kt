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
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.imperium.common.account.Role
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.annotation.Greedy
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.action.Action
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.input.TextInputInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.createPlayerListTransformer
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.event.EventHandler
import java.net.InetAddress
import java.time.Duration
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import mindustry.Vars
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Player
import mindustry.net.Administration

class VoteKickCommand(instances: InstanceManager) :
    AbstractVoteCommand<VoteKickCommand.Context>(instances.get(), "votekick", 1.minutes),
    ImperiumApplication.Listener {

    private val punishments = instances.get<PunishmentManager>()
    private val limiter = SimpleRateLimiter<InetAddress>(1, 60.seconds)
    private val votekickInterface = createVotekickInterface()

    @EventHandler
    internal fun onPlayerLeave(event: EventType.PlayerLeave) {
        manager.sessions.values
            .filter { it.objective.target == event.player }
            .forEach(VoteManager.Session<Context>::success)
    }

    @Command(["vote", "y"])
    @ClientSide
    private fun onVoteYesCommand(sender: CommandSender) {
        onPlayerVote(sender.player, getSession(sender.player.team()), Vote.YES)
    }

    @Command(["vote", "n"])
    @ClientSide
    private fun onVoteNoCommand(sender: CommandSender) {
        onPlayerVote(sender.player, getSession(sender.player.team()), Vote.NO)
    }

    @Command(["vote", "c"], Role.MODERATOR)
    @ClientSide
    private fun onVoteCancelCommand(sender: CommandSender, team: Team? = null) {
        onPlayerCancel(sender.player, getSession(team ?: sender.player.team()))
    }

    @Command(["votekick"])
    @ClientSide
    private fun onVotekickCommand(
        sender: CommandSender,
        target: Player? = null,
        @Greedy reason: String? = null
    ) {
        if (!Administration.Config.enableVotekick.bool()) {
            sender.player.sendMessage("[scarlet]Vote-kick is disabled on this server.")
            return
        }
        if (target == null) {
            votekickInterface.open(sender.player)
            return
        }
        if (Entities.PLAYERS.filter { !Vars.state.rules.pvp || it.team() == sender.player.team() }
            .size < 3) {
            sender.player.sendMessage(
                """
                [scarlet]At least 3 players are needed to start a votekick.
                Use the [orange]/report[] command instead.
                """
                    .trimIndent(),
            )
            return
        }
        if (reason == null) {
            sender.player.sendMessage(
                "[orange]You need a valid reason to kick the player. Add a reason after the player name.")
            return
        }
        onVoteSessionStart(
            sender.player, getSession(target.team()), Context(target, reason, sender.player.team()))
    }

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<Context>) {
        val duration = Duration.ofMinutes(NetServer.kickDuration / 60L)
        punishments.punish(
            null,
            Punishment.Target(
                session.objective.target.ip().toInetAddress(), session.objective.target.uuid()),
            "Votekick: ${session.objective.reason}",
            Punishment.Type.BAN,
            duration,
        )
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
            player.sendMessage(
                "[scarlet]You are limited to one votekick per minute. Please try again later.")
            return false
        }
        return super.canParticipantStart(player, objective)
    }

    override fun canParticipantVote(
        player: Player,
        session: VoteManager.Session<Context>
    ): Boolean {
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
        super.getParticipants(session).filter {
            !Vars.state.rules.pvp || it.team() == session.objective.team
        }

    override fun getRequiredVotes(players: Int): Int =
        if (players < 5) {
            2
        } else if (players < 21) {
            (players / 2F).roundToInt()
        } else {
            10
        }

    override fun getVoteSessionDetails(session: VoteManager.Session<Context>): String =
        "Type [accent]/vote y[] to kick ${session.objective.target.name.stripMindustryColors()} from the game. Reason being [accent]${session.objective.reason}[]."

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
                        Action.command("votekick", "#" + view.state[VOTEKICK_TARGET]!!.id, input)
                            .accept(view)
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
                    })
            }

        return playerListInterface
    }

    data class Context(val target: Player, val reason: String, val team: Team)

    companion object {
        private val VOTEKICK_TARGET = stateKey<Player>("votekick_target")
    }
}