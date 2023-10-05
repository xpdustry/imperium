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

import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.kotlin.extension.buildAndRegister
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.RateLimiter
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.command.SimpleVoteManager
import com.xpdustry.imperium.mindustry.command.VoteManager
import com.xpdustry.imperium.mindustry.misc.registerCopy
import com.xpdustry.imperium.mindustry.ui.action.Action
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.input.TextInputInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.createPlayerListTransformer
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import fr.xpdustry.distributor.api.command.argument.PlayerArgument
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.net.Administration
import java.net.InetAddress
import java.time.Duration
import kotlin.jvm.optionals.getOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

private val VOTEKICK_TARGET = stateKey<Player>("votekick_target")

// TODO
//  - Implement Session per team for PVP
//  - Implement rate limit (FIX RATELIMITER)
class VoteKickCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val clientCommands = instances.get<ImperiumPluginCommandManager>("client")
    private val plugin = instances.get<MindustryPlugin>()
    private val punishments = instances.get<PunishmentManager>()
    private val limiter = RateLimiter<InetAddress>(1, Duration.ofSeconds(60))
    private val manager = SimpleVoteManager<Player>(
        plugin = plugin,
        duration = 1.minutes,
        finished = {
            if (it.status == VoteManager.Session.Status.TIMEOUT) {
                Call.sendMessage("[lightgray]Vote failed. Not enough votes to kick[orange] ${it.target.name}[lightgray].")
            } else if (it.status == VoteManager.Session.Status.SUCCESS) {
                val duration = Duration.ofMinutes(NetServer.kickDuration / 60L)
                Call.sendMessage("[orange]Vote passed.[scarlet] ${it.target.name}[orange] will be banned from the server for ${duration.toMinutes()} minutes.")
                punishments.punish(null, Punishment.Target(it.target.ip().toInetAddress(), it.target.uuid()), "Votekick", Punishment.Type.KICK, duration)
            }
        },
        threshold = {
            val players = Groups.player.size()
            if (players < 5) {
                2
            } else if (players < 21) {
                (players / 2F).roundToInt()
            } else {
                10
            }
        },
    )

    override fun onImperiumInit() {
        val reasonInterface = TextInputInterface.create(plugin).apply {
            addTransformer { _, pane ->
                pane.title = "Votekick (2/2)"
                pane.description = "Enter a reason for the votekick"
                pane.inputAction = BiAction { view, input ->
                    Action.command("votekick", "#" + view.state[VOTEKICK_TARGET]!!.id, input)
                }
            }
        }

        val playerListInterface = MenuInterface.create(plugin).apply {
            addTransformer { _, pane ->
                pane.title = "Votekick (1/2)"
                pane.content = "Select a player to votekick"
            }
            addTransformer(
                createPlayerListTransformer { view, player ->
                    view.state[VOTEKICK_TARGET] = player
                    reasonInterface.open(player)
                },
            )
        }

        clientCommands.buildAndRegister("vote") {
            registerCopy("yes", aliases = arrayOf("y")) {
                handler { vote(it.sender.player, manager.session, true) }
            }

            registerCopy("no", aliases = arrayOf("n")) {
                handler { vote(it.sender.player, manager.session, false) }
            }

            registerCopy("cancel", aliases = arrayOf("c")) {
                handler { ctx ->
                    if (ctx.sender.player.admin) {
                        if (manager.session != null) {
                            Call.sendMessage("[lightgray]Vote canceled by admin[orange] ${ctx.sender.player.name}[lightgray].")
                            manager.session!!.failure()
                        } else {
                            ctx.sender.sendMessage("[scarlet]Nobody is being voted on.")
                        }
                    } else {
                        ctx.sender.sendMessage("[scarlet]You don't have permission to cancel the vote.")
                    }
                }
            }
        }

        clientCommands.buildAndRegister("votekick", aliases = arrayOf("vk")) {
            argument(PlayerArgument.optional("player"))
            argument(StringArgument.optional("reason", StringArgument.StringMode.GREEDY))
            handler { ctx ->
                if (!Administration.Config.enableVotekick.bool()) {
                    ctx.sender.player.sendMessage("[scarlet]Vote-kick is disabled on this server.")
                    return@handler
                }
                if (Groups.player.size() < 3) {
                    ctx.sender.player.sendMessage(
                        """
                        [scarlet]At least 3 players are needed to start a votekick.
                        Use the [orange]/report[] command instead.
                        """,
                    )
                    return@handler
                }
                if (ctx.sender.player.isLocal) {
                    ctx.sender.player.sendMessage("[scarlet]Just kick them yourself if you're the host.")
                    return@handler
                }
                if (manager.session != null) {
                    ctx.sender.player.sendMessage("[scarlet]A vote is already in progress.")
                    return@handler
                }
                val target = ctx.getOptional<Player>("player").getOrNull()
                if (target == null) {
                    playerListInterface.open(ctx.sender.player)
                    return@handler
                }
                val reason = ctx.getOptional<String>("reason").getOrNull()
                if (reason == null) {
                    ctx.sender.player.sendMessage("[orange]You need a valid reason to kick the player. Add a reason after the player name.")
                    return@handler
                }
                if (ctx.sender.player == target) {
                    ctx.sender.player.sendMessage("[scarlet]You can't vote to kick yourself.")
                    return@handler
                }
                if (target.admin) {
                    ctx.sender.player.sendMessage("[scarlet]Did you really expect to be able to kick an admin?")
                    return@handler
                }
                if (target.isLocal) {
                    ctx.sender.player.sendMessage("[scarlet]Local players cannot be kicked.")
                    return@handler
                }
                if (target.team() != ctx.sender.player.team()) {
                    ctx.sender.player.sendMessage("[scarlet]Only players on your team can be kicked.")
                    return@handler
                }
                if (limiter.incrementAndCheck(ctx.sender.player.ip().toInetAddress())) {
                    ctx.sender.player.sendMessage("[scarlet]You are limited to one votekick per minute. Please try again later.")
                    return@handler
                }
                val session = manager.start(ctx.sender.player, true, target)
                Call.sendMessage(
                    """
                    [lightgray]${ctx.sender.player.name}[lightgray] has voted on kicking [orange]${target.name}[lightgray].[accent] (${session.votes}/${session.required})
                    [lightgray]Reason: [orange]$reason[lightgray].
                    [lightgray]Type[orange] /vote <y/n>[] to agree."
                    """.trimIndent(),
                )
            }
        }
    }

    private fun vote(player: Player, session: VoteManager.Session<Player>?, value: Boolean) {
        if (session == null) {
            player.sendMessage("[scarlet]Nobody is being voted on.")
        } else if (player.isLocal) {
            player.sendMessage("[scarlet]Local players can't vote. Kick the player yourself instead.")
        } else if (session.getVote(player) != null) {
            player.sendMessage("[scarlet]You've already voted. Sit down.")
        } else if (session.target === player) {
            player.sendMessage("[scarlet]You can't vote on your own trial.")
        } else if (session.target.team() !== player.team()) {
            player.sendMessage("[scarlet]You can't vote for other teams.")
        } else {
            Call.sendMessage(
                """
                [lightgray]${player.name}[lightgray] has voted on kicking[orange] ${session.target.name}.[lightgray].[accent] (${session.votes}/${session.required})
                [lightgray]Type[orange] /vote <y/n>[] to agree.
                """.trimIndent(),
            )
            session.setVote(player, value)
        }
    }

    @EventHandler
    internal fun onPlayerLeave(event: EventType.PlayerLeave) {
        if (manager.session?.target == event.player) {
            manager.session!!.success()
        }
    }
}
