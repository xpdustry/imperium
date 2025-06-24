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

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.menu.ListTransformer
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.ReportMessage
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.CoroutineAction
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.NavigateAction
import com.xpdustry.imperium.mindustry.misc.NavigationTransformer
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.key
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.translation.gui_back
import com.xpdustry.imperium.mindustry.translation.gui_report_content_confirm
import com.xpdustry.imperium.mindustry.translation.gui_report_content_player
import com.xpdustry.imperium.mindustry.translation.gui_report_content_reason
import com.xpdustry.imperium.mindustry.translation.gui_report_failure
import com.xpdustry.imperium.mindustry.translation.gui_report_no_players
import com.xpdustry.imperium.mindustry.translation.gui_report_rate_limit
import com.xpdustry.imperium.mindustry.translation.gui_report_success
import com.xpdustry.imperium.mindustry.translation.gui_report_title
import com.xpdustry.imperium.mindustry.translation.yes
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds
import mindustry.gen.Player

class ReportCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val reportInterface = MenuManager.create(instances.get())
    private val config = instances.get<ImperiumConfig>()
    private val messenger = instances.get<Messenger>()
    private val limiter = SimpleRateLimiter<InetAddress>(1, 60.seconds)
    private val users = instances.get<UserManager>()

    init {
        reportInterface.addTransformer(
            NavigationTransformer(
                REPORT_PAGE,
                ReportPage.PLAYER,
                ListTransformer<Player>()
                    .setProvider { ctx ->
                        Entities.getPlayers()
                            .asSequence()
                            .filter { it != ctx.viewer }
                            .sortedBy { it.info.plainLastName() }
                            .toList()
                    }
                    .setRenderer { player -> text(player.info.plainLastName()) }
                    .setHeight(10)
                    .setChoiceAction(
                        BiAction.with(REPORT_PLAYER).then(BiAction.from(NavigateAction(REPORT_PAGE, ReportPage.REASON)))
                    ),
            )
        )

        reportInterface.addTransformer(
            NavigationTransformer(
                REPORT_PAGE,
                ReportPage.REASON,
                ListTransformer<ReportMessage.Reason>()
                    .setProvider { ReportMessage.Reason.entries }
                    .setRenderer { reason -> text(reason.name.lowercase().capitalize()) }
                    .setHeight(Int.MAX_VALUE)
                    .setRenderNavigation(false)
                    .setChoiceAction(
                        BiAction.with(REPORT_REASON)
                            .then(BiAction.from(NavigateAction(REPORT_PAGE, ReportPage.CONFIRM)))
                    ),
            )
        )

        reportInterface.addTransformer { (pane, state) ->
            val page = state[REPORT_PAGE]!!
            pane.title = gui_report_title(page.ordinal + 1, ReportPage.entries.size)
            pane.content =
                when (page) {
                    ReportPage.PLAYER -> gui_report_content_player()
                    ReportPage.REASON -> gui_report_content_reason()
                    ReportPage.CONFIRM -> gui_report_content_confirm(state[REPORT_PLAYER]!!, state[REPORT_REASON]!!)
                }
            val back =
                when (page) {
                    ReportPage.PLAYER -> Action.back()
                    ReportPage.REASON -> NavigateAction(REPORT_PAGE, ReportPage.PLAYER)
                    ReportPage.CONFIRM -> NavigateAction(REPORT_PAGE, ReportPage.REASON)
                }
            pane.grid.addRow(MenuOption.of(gui_back(), back))
        }

        reportInterface.addTransformer(
            NavigationTransformer(REPORT_PAGE, ReportPage.CONFIRM) { (pane) ->
                pane.grid.addOption(
                    MenuOption.of(
                        yes(),
                        Action.hideAll()
                            .then(
                                CoroutineAction(
                                    success = { window, sent ->
                                        if (sent) {
                                            limiter.increment(window.viewer.ip().toInetAddress())
                                            window.viewer.asAudience.sendAnnouncement(gui_report_success())
                                        } else {
                                            window.viewer.asAudience.sendAnnouncement(gui_report_failure())
                                        }
                                    }
                                ) {
                                    val sender = runMindustryThread { it.viewer.info }
                                    val target = runMindustryThread { it.state[REPORT_PLAYER]!!.info }
                                    messenger.publish(
                                        ReportMessage(
                                            config.server.name,
                                            sender.plainLastName(),
                                            users.findByUuid(sender.id)!!.id,
                                            target.plainLastName(),
                                            users.findByUuid(target.id)!!.id,
                                            it.state[REPORT_REASON]!!,
                                        )
                                    )
                                }
                            ),
                    )
                )
            }
        )
    }

    @ImperiumCommand(["report"])
    @ClientSide
    fun onPlayerReport(sender: CommandSender) {
        if (Entities.getPlayers().size == 1) {
            sender.error(gui_report_no_players())
        } else if (!limiter.check(sender.player.ip().toInetAddress())) {
            sender.error(gui_report_rate_limit())
        } else {
            reportInterface.create(sender.player).apply { state[REPORT_PAGE] = ReportPage.PLAYER }.show()
        }
    }

    companion object {
        private val REPORT_PAGE = key<ReportPage>("report_window")
        private val REPORT_PLAYER = key<Player>("report_player")
        private val REPORT_REASON = key<ReportMessage.Reason>("report_reason")
    }
}

enum class ReportPage {
    PLAYER,
    REASON,
    CONFIRM,
}
