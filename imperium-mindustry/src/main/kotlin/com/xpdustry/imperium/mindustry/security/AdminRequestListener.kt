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

import arc.Events
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.misc.showInfoMessage
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.input.TextInputInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType.AdminRequestEvent
import mindustry.game.Team
import mindustry.gen.AdminRequestCallPacket
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.net.Administration.TraceInfo
import mindustry.net.NetConnection
import mindustry.net.Packets
import mindustry.net.Packets.AdminAction

private val PUNISHMENT_DURATION = stateKey<Duration>("punishment_duration")
private val PUNISHMENT_REASON = stateKey<String>("punishment_reason")
private val PUNISHMENT_TARGET = stateKey<Identity.Mindustry>("punishment_target")
private val PUNISHMENT_TYPE = stateKey<Punishment.Type>("punishment_type")

class AdminRequestListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val plugin = instances.get<MindustryPlugin>()
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val accounts = instances.get<AccountManager>()
    private val codec = instances.get<IdentifierCodec>()
    private lateinit var adminActionInterface: Interface

    override fun onImperiumInit() {
        val detailsInterface = TextInputInterface.create(plugin)
        detailsInterface.addTransformer { v, pane ->
            pane.title = "Admin Action (3/3)"
            pane.description = "Enter the reason of the ${v.state[PUNISHMENT_TYPE].toString().lowercase()}"
            pane.placeholder = v.state[PUNISHMENT_TYPE].toString().lowercase()
            pane.inputAction = BiAction { view, input ->
                view.closeAll()
                view.state[PUNISHMENT_REASON] = input
                ImperiumScope.MAIN.launch {
                    val target = users.getByIdentity(view.state[PUNISHMENT_TARGET]!!)
                    punishments.punish(
                        view.viewer.identity,
                        target.id,
                        view.state[PUNISHMENT_REASON]!!,
                        view.state[PUNISHMENT_TYPE]!!,
                        view.state[PUNISHMENT_DURATION]!!,
                    )
                    // TODO Move to PunishmentListener ?
                    logger.info(
                        "{} ({}) has {} {} ({})",
                        view.viewer.plainName(),
                        view.viewer.uuid(),
                        view.state[PUNISHMENT_TYPE]!!.name.lowercase(),
                        target.lastName,
                        target.uuid,
                    )
                }
            }
        }

        val durationInterface =
            MenuInterface.create(plugin).apply {
                addTransformer { view, pane ->
                    pane.title = "Ban (2/3)"
                    pane.content =
                        "Select duration of the ${view.state[PUNISHMENT_TYPE].toString().lowercase()} of ${view.state[PUNISHMENT_TARGET]!!.name}"

                    // TODO Goofy aah function, use proper library to display durations
                    fun addDuration(display: String, duration: Duration) =
                        pane.options.addRow(
                            MenuOption(display) { _ ->
                                view.close()
                                view.state[PUNISHMENT_DURATION] = duration
                                detailsInterface.open(view)
                            }
                        )

                    addDuration("[green]15 minutes", 15.minutes)
                    addDuration("[green]1 hour", 1.hours)
                    addDuration("[green]3 hour", 3.hours)
                    addDuration("[green]6 hours", 6.hours)
                    addDuration("[green]1 day", 1.days)
                    addDuration("[green]3 days", 3.days)
                    addDuration("[green]1 week", 7.days)
                    addDuration("[green]1 month", 30.days)
                    addDuration("[red]Permanent", Duration.INFINITE)
                    pane.options.addRow(MenuOption("[red]Cancel", View::back))
                }
            }

        adminActionInterface =
            MenuInterface.create(plugin).apply {
                addTransformer { _, pane ->
                    pane.title = "Admin Action (1/3)"
                    pane.content = "What kind of action you want to take ?"

                    Punishment.Type.entries.forEach { type ->
                        pane.options.addRow(
                            MenuOption(type.name.lowercase()) { view ->
                                view.close()
                                view.state[PUNISHMENT_TYPE] = type
                                durationInterface.open(view)
                            }
                        )
                    }

                    pane.options.addRow(MenuOption("[red]Cancel", View::back))
                }
            }

        Vars.net.handleServer(AdminRequestCallPacket::class.java, ::interceptAdminRequest)
    }

    private fun interceptAdminRequest(con: NetConnection, packet: AdminRequestCallPacket) {
        if (con.player == null) {
            logger.warn("Received admin request from non-existent player (uuid: {}, ip: {})", con.uuid, con.address)
            return
        }

        if (!con.player.admin()) {
            logger.warn(
                "{} ({}) attempted to perform an admin action without permission",
                con.player.plainName(),
                con.player.uuid(),
            )
            return
        }

        if (packet.other == null) {
            logger.warn(
                "{} ({}) attempted to perform an admin action on non-existent",
                con.player.plainName(),
                con.player.uuid(),
            )
            return
        }

        if (packet.other.admin() && (packet.action != AdminAction.switchTeam && packet.action != AdminAction.wave)) {
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
            AdminAction.wave -> handleWaveSkip(con.player)
            AdminAction.trace -> handleTraceInfo(con.player, packet.other)
            AdminAction.ban -> adminActionInterface.open(con.player) { it[PUNISHMENT_TARGET] = packet.other.identity }
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
            else -> {
                con.player.showInfoMessage("Unknown admin action.")
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
    }

    private fun handleTraceInfo(requester: Player, target: Player) =
        ImperiumScope.MAIN.launch {
            val user = users.findByUuid(target.uuid())
            if (user == null) {
                // This should never happen
                Call.infoMessage(requester.con, "Player not found.")
                return@launch
            }
            val canSeeInfo = (accounts.selectBySession(requester.sessionKey)?.rank ?: Rank.EVERYONE) >= Rank.ADMIN
            val historic = users.findNamesAndAddressesById(user.id)
            Call.traceInfo(
                requester.con,
                target,
                TraceInfo(
                    if (canSeeInfo) target.con.address else "Don't have permission to view addresses.",
                    if (canSeeInfo) target.uuid() else codec.encode(user.id),
                    target.con.modclient,
                    target.con.mobile,
                    user.timesJoined,
                    punishments.findAllByIdentity(target.identity).count(),
                    if (canSeeInfo) historic.addresses.map(InetAddress::getHostAddress).toTypedArray()
                    else arrayOf("Don't have permission to view addresses."),
                    historic.names.toTypedArray(),
                ),
            )
            logger.info(
                "{} ({}) has requested trace info of {} ({})",
                requester.plainName(),
                requester.uuid(),
                target.plainName(),
                target.uuid(),
            )
        }

    private fun handleWaveSkip(requester: Player) =
        ImperiumScope.MAIN.launch {
            val rank = accounts.selectBySession(requester.sessionKey)?.rank ?: Rank.EVERYONE
            if (rank >= Rank.MODERATOR) {
                runMindustryThread {
                    Vars.logic.skipWave()
                    logger.info("{} ({}) has skipped the wave", requester.plainName(), requester.uuid())
                }
            } else {
                requester.showInfoMessage("You don't have permission to skip the wave.")
                logger.warn(
                    "{} ({}) attempted to skip the wave without permission",
                    requester.plainName(),
                    requester.uuid(),
                )
            }
        }

    companion object {
        private val logger by LoggerDelegate()
    }
}
