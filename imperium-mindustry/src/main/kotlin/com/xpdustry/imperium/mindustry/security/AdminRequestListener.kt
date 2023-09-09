package com.xpdustry.imperium.mindustry.security

import arc.Events
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Ban
import com.xpdustry.imperium.common.security.BanManager
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.ReportMessage
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.showInfoMessage
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.action.Action
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.input.TextInputInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType.AdminRequestEvent
import mindustry.game.Team
import mindustry.gen.AdminRequestCallPacket
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.net.Administration.TraceInfo
import mindustry.net.NetConnection
import mindustry.net.Packets.AdminAction

private val BAN_PERMANENT = stateKey<Boolean>("permanent_ban")
private val BAN_REASON = stateKey<Ban.Reason>("ban_reason")
private val BAN_DETAIL = stateKey<String>("ban_detail")
private val BAN_TARGET = stateKey<Identity.Mindustry>("ban_target")

class AdminRequestListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val plugin = instances.get<MindustryPlugin>()
    private val bans = instances.get<BanManager>()

    override fun onImperiumInit() {
        val banConfirmationInterface = MenuInterface.create(plugin)
        banConfirmationInterface.addTransformer { view, pane ->
            val type = if (view.state[BAN_PERMANENT]!!) "permanently" else "temporarily"
            pane.content = "Are you sure you want to ban [scarlet]$type[] [accent]${view.state[BAN_TARGET]!!.name}[] for [accent]${view.state[BAN_REASON]!!.name.lowercase().capitalize()}[] (details=${view.state[BAN_DETAIL]}) ?"
            pane.options.addRow(
                MenuOption("[green]Yes") { _ ->
                    view.closeAll()
                    ImperiumScope.MAIN.launch {
                        bans.punish(
                            view.viewer.identity,
                            view.state[BAN_TARGET]!!.address,
                            view.state[BAN_REASON]!!,
                            view.state[BAN_DETAIL],
                            null,
                        )
                    }
                },
                MenuOption("[orange]No") { it.back(2) },
                MenuOption("[red]Abort") { it.closeAll() },
        }

        val detailsInterface = TextInputInterface.create(plugin)
        detailsInterface.addTransformer { _, pane ->
            pane.title = "Ban (2/3)"
            pane.description = "Enter the details of your ban, \nclick on cancel if you don't have any."
            pane.inputAction = BiAction { view, input ->
                view.close()
                view.state[BAN_DETAIL] = input
                banConfirmationInterface.open(view)
            }
            pane.exitAction = Action { view ->
                view.close()
                view.state.remove(BAN_DETAIL)
                banConfirmationInterface.open(view)
            }
        }

        val reasonInterface = MenuInterface.create(plugin)
        reasonInterface.addTransformer { view, pane ->
            pane.title = "Ban (1/3)"
            val type = if (view.state[BAN_PERMANENT]!!) "permanent" else "temporary"
            pane.content = "Select reason of the $type ban of ${view.state[BAN_TARGET]!!.name}"
            for (reason in Ban.Reason.entries) {
                pane.options.addRow(
                    MenuOption(reason.name.lowercase().capitalize()) { _ ->
                        view.close()
                        view.state[BAN_REASON] = reason
                        detailsInterface.open(view)
                    },
                )
            }
            pane.options.addRow(MenuOption("[red]Cancel", View::back))
        }


        Vars.net.handleServer(AdminRequestCallPacket::class.java, ::interceptAdminRequest)
    }

    private fun interceptAdminRequest(con: NetConnection, packet: AdminRequestCallPacket) {
        if (con.player == null) {
            logger.warn("Received admin request from non-existent player (uuid: {}, ip: {})", con.uuid, con.address)
            return
        }

        if (!con.player.admin()) {
            logger.warn("{} ({}) attempted to perform an admin action without permission", con.player.plainName(), con.player.uuid())
            return
        }

        if (packet.other == null) {
            logger.warn("{} ({}) attempted to perform an admin action on non-existent", con.player.plainName(), con.player.uuid())
            return
        }

        if (packet.other.admin() && packet.action != AdminAction.switchTeam) {
            logger.warn(
                "{} ({}) attempted to perform an admin action on the admin {} ({})",
                con.player.plainName(),
                con.player.uuid(),
                packet.other.plainName(),
                packet.other.uuid()
            )
            return
        }

        Events.fire(AdminRequestEvent(con.player, packet.other, packet.action))
        when (packet.action) {
            AdminAction.wave -> {
                Vars.logic.skipWave()
                logger.info("{} ({}) has skipped the wave", con.player.plainName(), con.player.uuid())
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
                        stats.names.toArray(String::class.java)
                    )
                )
                logger.info(
                    "{} ({}) has requested trace info of {} ({})",
                    con.player.plainName(),
                    con.player.uuid(),
                    packet.other.plainName(),
                    packet.other.uuid()
                )
            }

            AdminAction.ban -> punish(con.player, packet.other, true)

            AdminAction.kick -> punish(con.player, packet.other, false)

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
                        param.name
                    )
                } else {
                    logger.warn(
                        "{} ({}) attempted to switch {} ({}) to an invalid team: {}",
                        con.player.plainName(),
                        con.player.uuid(),
                        packet.other.plainName(),
                        packet.other.uuid(),
                        packet.params
                    )
                }
            }

            else -> logger.warn(
                "{} ({}) attempted to perform an unknown admin action {} on {} ({})",
                con.player.plainName(),
                con.player.uuid(),
                packet.action,
                packet.other.plainName(),
                packet.other.uuid()
            )
        }
    }

    private fun punish(player: Player, target: Player, permanent: Boolean) = reasonInput.open(player) {
        it[BAN_PERMANENT] = permanent
        it[BAN_TARGET] = target.identity
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}

