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

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.annotation.Greedy
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.MindustryUUIDAsLong
import com.xpdustry.imperium.common.misc.buildCache
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.misc.toLongMuuid
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.common.user.UserManager
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
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.util.MUUID
import java.net.InetAddress
import java.util.Collections
import kotlin.math.roundToInt
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

class VoteKickCommand(instances: InstanceManager) :
    AbstractVoteCommand<VoteKickCommand.Context>(instances.get(), "votekick", 1.minutes),
    ImperiumApplication.Listener {

    private val punishments = instances.get<PunishmentManager>()
    private val limiter = SimpleRateLimiter<InetAddress>(1, 60.seconds)
    private val votekickInterface = createVotekickInterface()
    private val config = instances.get<ImperiumConfig>()
    private val users = instances.get<UserManager>()
    private val freezes = instances.get<FreezeManager>()
    private val recentBans =
        Collections.newSetFromMap(
            buildCache<MUUID, Boolean> { expireAfterWrite(1.minutes.toJavaDuration()) }.asMap())

    @EventHandler
    internal fun onPlayerBanEvent(event: PlayerBanEvent) {
        recentBans.add(MUUID.of(event.player))
    }

    @EventHandler
    internal fun onPlayerLeave(event: EventType.PlayerLeave) {
        if (MUUID.of(event.player) in recentBans) return
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

    @Command(["vote", "c"], Rank.MODERATOR)
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
        if (Entities.getPlayers()
            .filter { !Vars.state.rules.pvp || it.team() == sender.player.team() }
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
        runMindustryThread { freezes.setTemporaryFreeze(session.objective.target, null) }
        val yes = mutableSetOf<MindustryUUIDAsLong>()
        val nay = mutableSetOf<MindustryUUIDAsLong>()
        for ((voter, vote) in session.voters) {
            (if (vote.asBoolean()) yes else nay) += voter.toLongMuuid()
        }
        punishments.punish(
            config.server.identity,
            users.getByIdentity(session.objective.target.identity).snowflake,
            "Votekick: ${session.objective.reason}",
            Punishment.Type.BAN,
            NetServer.kickDuration.seconds,
            Punishment.Metadata.Votekick(session.initiator!!.uuid().toLongMuuid(), yes, nay))
    }

    override suspend fun onVoteSessionFailure(session: VoteManager.Session<Context>) {
        runMindustryThread { freezes.setTemporaryFreeze(session.objective.target, null) }
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
        freezes.setTemporaryFreeze(
            objective.target, FreezeManager.Freeze("You are currently being votekicked."))
        return true
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
        "Type [accent]/vote y[] to kick [accent]${session.objective.target.name.stripMindustryColors()}[] from the game for [accent]${session.objective.reason}[]."

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
