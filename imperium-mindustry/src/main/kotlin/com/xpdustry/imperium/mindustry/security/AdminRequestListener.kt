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
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.WindowManager
import com.xpdustry.distributor.api.gui.input.TextInputManager
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
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
import com.xpdustry.imperium.mindustry.misc.CoroutineAction
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.component3
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.key
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.misc.showInfoMessage
import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

private val PUNISHMENT_DURATION = key<Duration>("punishment_duration")
private val PUNISHMENT_REASON = key<String>("punishment_reason")
private val PUNISHMENT_TARGET = key<Identity.Mindustry>("punishment_target")
private val PUNISHMENT_TYPE = key<Punishment.Type>("punishment_type")

class AdminRequestListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val plugin = instances.get<MindustryPlugin>()
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val accounts = instances.get<AccountManager>()
    private val codec = instances.get<IdentifierCodec>()
    private lateinit var adminActionInterface: WindowManager

    override fun onImperiumInit() {
        val detailsInterface = TextInputManager.create(plugin)
        detailsInterface.addTransformer { (pane, state) ->
            pane.title = text("Admin Action (3/3)")
            pane.description = text("Enter the reason of the ${state[PUNISHMENT_TYPE].toString().lowercase()}")
            pane.placeholder = text(state[PUNISHMENT_TYPE].toString().lowercase())
            pane.maxLength = 128
            pane.inputAction =
                BiAction.delegate { _, input ->
                    Action.hideAll()
                        .then(Action.with(PUNISHMENT_REASON, input))
                        .then(
                            CoroutineAction { window ->
                                val target = users.getByIdentity(window.state[PUNISHMENT_TARGET]!!)
                                punishments.punish(
                                    window.viewer.identity,
                                    target.id,
                                    window.state[PUNISHMENT_REASON]!!,
                                    window.state[PUNISHMENT_TYPE]!!,
                                    window.state[PUNISHMENT_DURATION]!!,
                                )
                                // TODO Move to PunishmentListener ?
                                logger.info(
                                    "{} ({}) has {} {} ({})",
                                    window.viewer.plainName(),
                                    window.viewer.uuid(),
                                    window.state[PUNISHMENT_TYPE]!!.name.lowercase(),
                                    target.lastName,
                                    target.uuid,
                                )
                            }
                        )
                }
        }

        val durationInterface =
            MenuManager.create(plugin).apply {
                addTransformer { (pane, state) ->
                    pane.title = text("Ban (2/3)")
                    pane.content =
                        text(
                            "Select duration of the ${state[PUNISHMENT_TYPE].toString().lowercase()} of ${state[PUNISHMENT_TARGET]!!.name}"
                        )

                    // TODO Goofy aah function, use proper library to display durations
                    fun addDuration(display: String, duration: Duration, color: ComponentColor = ComponentColor.GREEN) =
                        pane.grid.addRow(
                            MenuOption.of(
                                text(display, color),
                                Action.hide()
                                    .then(
                                        Action.with(PUNISHMENT_DURATION, duration).then(Action.show(detailsInterface))
                                    ),
                            )
                        )

                    addDuration("15 minutes", 15.minutes)
                    addDuration("1 hour", 1.hours)
                    addDuration("3 hour", 3.hours)
                    addDuration("6 hours", 6.hours)
                    addDuration("1 day", 1.days)
                    addDuration("3 days", 3.days)
                    addDuration("1 week", 7.days)
                    addDuration("1 month", 30.days)
                    addDuration("Permanent", Duration.INFINITE, ComponentColor.RED)
                    pane.grid.addRow(MenuOption.of(text("Cancel", ComponentColor.RED), Action.back()))
                }
            }

        adminActionInterface =
            MenuManager.create(plugin).apply {
                addTransformer { (pane) ->
                    pane.title = text("Admin Action (1/3)")
                    pane.content = text("What kind of action you want to take ?")

                    Punishment.Type.entries.forEach { type ->
                        pane.grid.addRow(
                            MenuOption.of(
                                type.name.lowercase(),
                                Action.hide()
                                    .then(Action.with(PUNISHMENT_TYPE, type))
                                    .then(Action.show(durationInterface)),
                            )
                        )
                    }

                    pane.grid.addRow(MenuOption.of(text("Cancel", ComponentColor.RED), Action.back()))
                }
            }

        Vars.net.handleServer(AdminRequestCallPacket::class.java, ::interceptAdminRequest)
    }

    private fun interceptAdminRequest(con: NetConnection, packet: AdminRequestCallPacket) {
        if (con.player == null) {
            logger.warn("Received admin request from non-existent player (uuid: {}, ip: {})", con.uuid, con.address)
            return
        }
        val senderRank = runBlocking { getUserRank(con.player) }

        // Allow undercover staff to use the admin menu
        if (senderRank < Rank.OVERSEER || !con.player.admin()) {
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

        if ((packet.other.admin() && senderRank <= Rank.ADMIN) && (packet.action != AdminAction.switchTeam && packet.action != AdminAction.wave)) {
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
            AdminAction.ban ->
                adminActionInterface
                    .create(con.player)
                    .apply { state[PUNISHMENT_TARGET] = packet.other.identity }
                    .show()
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
            val canSeeInfo = getUserRank(requester) >= Rank.ADMIN
            val historic = users.findNamesAndAddressesById(user.id)
            Call.traceInfo(
                requester.con,
                target,
                TraceInfo(
                    if (canSeeInfo) target.con.address
                    else "Don't have permission to view addresses. | ${codec.encode(user.id)}",
                    // fix foos autotrace complaining about ips being the same
                    // https://github.com/mindustry-antigrief/mindustry-client/blob/cd7df920b49c167674392e6837cba1812d5b19dc/core/src/mindustry/client/antigrief/Moderation.kt#L116
                    if (canSeeInfo) target.uuid() else codec.encode(user.id),
                    target.locale,
                    target.con.modclient,
                    target.con.mobile,
                    user.timesJoined,
                    punishments.findAllByIdentity(target.identity).count(),
                    if (canSeeInfo) historic.addresses.map(InetAddress::getHostAddress).toTypedArray()
                    else arrayOf("Don't have permission to view addresses | ${codec.encode(user.id)}"),
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

    private suspend fun getUserRank(requester: Player): Rank {
        val account = accounts.selectBySession(requester.sessionKey) ?: return Rank.EVERYONE
        return account.rank
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
