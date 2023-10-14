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

import arc.Events
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.input.TextInputInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import java.time.Duration
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType.AdminRequestEvent
import mindustry.game.Team
import mindustry.gen.AdminRequestCallPacket
import mindustry.gen.Call
import mindustry.net.Administration.TraceInfo
import mindustry.net.NetConnection
import mindustry.net.Packets
import mindustry.net.Packets.AdminAction

private val PUNISHMENT_DURATION = stateKey<Duration>("punishment_duration")
private val PUNISHMENT_REASON = stateKey<String>("punishment_reason")
private val PUNISHMENT_TARGET = stateKey<Identity.Mindustry>("punishment_target")

class AdminRequestListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val plugin = instances.get<MindustryPlugin>()
    private val bans = instances.get<PunishmentManager>()
    private lateinit var banInterface: Interface

    override fun onImperiumInit() {
        val banConfirmationInterface = MenuInterface.create(plugin)
        banConfirmationInterface.addTransformer { view, pane ->
            val type = if (view.state[PUNISHMENT_DURATION] == null) "permanently" else "temporarily"
            pane.content =
                "Are you sure you want to [scarlet]$type[] ban [accent]${view.state[PUNISHMENT_TARGET]!!.name}[] for [accent]${view.state[PUNISHMENT_REASON]!!}[] ?"
            pane.options.addRow(
                MenuOption("[green]Yes") { _ ->
                    view.closeAll()
                    ImperiumScope.MAIN.launch {
                        bans.punish(
                            view.viewer.identity,
                            Punishment.Target(
                                view.state[PUNISHMENT_TARGET]!!.address,
                                view.state[PUNISHMENT_TARGET]!!.uuid,
                            ),
                            view.state[PUNISHMENT_REASON]!!,
                            Punishment.Type.BAN,
                            view.state[PUNISHMENT_DURATION],
                        )
                    }
                },
                MenuOption("[orange]No", View::back),
                MenuOption("[red]Abort", View::closeAll),
            )
        }

        val detailsInterface = TextInputInterface.create(plugin)
        detailsInterface.addTransformer { _, pane ->
            pane.title = "Ban (2/3)"
            pane.description = "Enter the reason of your ban."
            pane.inputAction = BiAction { view, input ->
                view.close()
                view.state[PUNISHMENT_REASON] = input
                banConfirmationInterface.open(view)
            }
        }

        banInterface =
            MenuInterface.create(plugin).apply {
                addTransformer { view, pane ->
                    pane.title = "Ban (1/3)"
                    pane.content =
                        "Select duration of the ban of ${view.state[PUNISHMENT_TARGET]!!.name}"

                    // TODO Goofy aah function, use proper library to display durations
                    fun addDuration(display: String, duration: Duration?) =
                        pane.options.addRow(
                            MenuOption(display) { _ ->
                                view.close()
                                if (duration != null) view.state[PUNISHMENT_DURATION] = duration
                                detailsInterface.open(view)
                            },
                        )

                    addDuration("[green]1 hour", Duration.ofHours(1L))
                    addDuration("[green]6 hours", Duration.ofHours(6L))
                    addDuration("[green]1 day", Duration.ofDays(1L))
                    addDuration("[green]3 days", Duration.ofDays(3L))
                    addDuration("[green]1 week", Duration.ofDays(7L))
                    addDuration("[green]1 month", Duration.ofDays(30L))
                    addDuration("[red]Permanent", null)
                    pane.options.addRow(MenuOption("[red]Cancel", View::back))
                }
            }

        Vars.net.handleServer(AdminRequestCallPacket::class.java, ::interceptAdminRequest)
    }

    private fun interceptAdminRequest(con: NetConnection, packet: AdminRequestCallPacket) {
        if (con.player == null) {
            logger.warn(
                "Received admin request from non-existent player (uuid: {}, ip: {})",
                con.uuid,
                con.address)
            return
        }

        if (!con.player.admin()) {
            logger.warn(
                "{} ({}) attempted to perform an admin action without permission",
                con.player.plainName(),
                con.player.uuid())
            return
        }

        if (packet.other == null) {
            logger.warn(
                "{} ({}) attempted to perform an admin action on non-existent",
                con.player.plainName(),
                con.player.uuid())
            return
        }

        if (packet.other.admin() && packet.action != AdminAction.switchTeam) {
            logger.warn(
                "{} ({}) attempted to perform an admin action on the admin {} ({})",
                con.player.plainName(),
                con.player.uuid(),
                packet.other.plainName(),
                packet.other.uuid(),
            )
            return
        }

        Events.fire(AdminRequestEvent(con.player, packet.other, packet.action))
        when (packet.action) {
            AdminAction.wave -> {
                Vars.logic.skipWave()
                logger.info(
                    "{} ({}) has skipped the wave", con.player.plainName(), con.player.uuid())
            }
            AdminAction.trace -> {
                val stats = Vars.netServer.admins.getInfo(packet.other.uuid())
                Call.traceInfo(
                    con,
                    packet.other,
                    TraceInfo(
                        packet.other.con.address,
                        packet.other.uuid(),
                        packet.other.con.modclient,
                        packet.other.con.mobile,
                        stats.timesJoined,
                        stats.timesKicked,
                        stats.ips.toArray(String::class.java),
                        stats.names.toArray(String::class.java),
                    ),
                )
                logger.info(
                    "{} ({}) has requested trace info of {} ({})",
                    con.player.plainName(),
                    con.player.uuid(),
                    packet.other.plainName(),
                    packet.other.uuid(),
                )
            }
            AdminAction.ban ->
                banInterface.open(con.player) { it[PUNISHMENT_TARGET] = packet.other.identity }
            AdminAction.kick -> {
                packet.other.kick(Packets.KickReason.kick, 0L)
                logger.info(
                    "{} ({}) has kicked {} ({})",
                    con.player.plainName(),
                    con.player.uuid(),
                    packet.other.plainName(),
                    packet.other.uuid(),
                )
            }
            AdminAction.switchTeam -> {
                val param = packet.params
                if (param is Team) {
                    packet.other.team(param)
                    logger.info(
                        "{} ({}) has switched {} ({}) to team {}",
                        con.player.plainName(),
                        con.player.uuid(),
                        packet.other.plainName(),
                        packet.other.uuid(),
                        param.name,
                    )
                } else {
                    logger.warn(
                        "{} ({}) attempted to switch {} ({}) to an invalid team: {}",
                        con.player.plainName(),
                        con.player.uuid(),
                        packet.other.plainName(),
                        packet.other.uuid(),
                        packet.params,
                    )
                }
            }
            else ->
                logger.warn(
                    "{} ({}) attempted to perform an unknown admin action {} on {} ({})",
                    con.player.plainName(),
                    con.player.uuid(),
                    packet.action,
                    packet.other.plainName(),
                    packet.other.uuid(),
                )
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
