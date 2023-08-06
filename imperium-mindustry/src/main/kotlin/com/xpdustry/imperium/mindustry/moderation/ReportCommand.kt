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
package com.xpdustry.imperium.mindustry.moderation

import cloud.commandframework.kotlin.extension.buildAndRegister
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.RateLimiter
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.misc.then
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.moderation.ReportMessage
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.misc.MindustryScheduler
import com.xpdustry.imperium.mindustry.misc.playerInfo
import com.xpdustry.imperium.mindustry.misc.showInfoMessage
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.action.Action
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.input.TextInputInterface
import com.xpdustry.imperium.mindustry.ui.menu.ListTransformer
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import fr.xpdustry.distributor.api.util.ArcCollections
import mindustry.gen.Groups
import mindustry.gen.Player
import reactor.core.publisher.Mono
import java.net.InetAddress
import java.time.Duration
import java.util.function.Function

private val logger = logger<ReportCommand>()
private val limiter = RateLimiter<InetAddress>(1, Duration.ofSeconds(60))

// TODO
//  - Implement tile reporting ?
//  - Add rate limit warning BEFORE running the command
class ReportCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val clientCommandManager = instances.get<ImperiumPluginCommandManager>("client")
    private val messenger = instances.get<Messenger>()
    private val plugin = instances.get<MindustryPlugin>()
    private val config = instances.get<ImperiumConfig>()
    override fun onImperiumInit() {
        val reportInterface = createReportInterface(plugin, messenger, config)
        clientCommandManager.buildAndRegister("report") {
            commandDescription("Report a player")
            handler { ctx -> reportInterface.open(ctx.sender.player) }
        }
    }
}

private val REPORT_PLAYER = stateKey<Player>("report_player")
private val REPORT_REASON = stateKey<ReportMessage.Reason>("report_reason")
private val REPORT_DETAIL = stateKey<String>("report_detail")

fun createReportInterface(plugin: MindustryPlugin, messenger: Messenger, config: ImperiumConfig): Interface {
    val reportConfirmInterface = MenuInterface.create(plugin)
    reportConfirmInterface.addTransformer { view, pane ->
        pane.title = "Report (4/4)"
        // TODO I really need to implement localization
        pane.content = "Are you sure you want to report [accent]${view.state[REPORT_PLAYER]!!.plainName()}[] for [accent]${view.state[REPORT_REASON]!!.name.lowercase().capitalize()}[]?"
        pane.options.addRow(
            MenuOption("[green]Yes") { _ ->
                if (!limiter.checkAndIncrement(view.viewer.ip().toInetAddress())) {
                    view.viewer.showInfoMessage("[red]You are limited to one report per minute. Please try again later.")
                    return@MenuOption
                }
                view.closeAll()
                messenger.publish(
                    ReportMessage(
                        config.mindustry.serverName,
                        view.viewer.playerInfo,
                        view.state[REPORT_PLAYER]!!.playerInfo,
                        view.state[REPORT_REASON]!!,
                        view.state[REPORT_DETAIL],
                    ),
                )
                    .publishOn(MindustryScheduler)
                    .doOnError {
                        logger.error("An error occurred while sending a report", it)
                        view.viewer.sendMessage(
                            "[scarlet]An error occurred while sending your report, please try again later.",
                        )
                    }
                    .then {
                        view.viewer.sendMessage("[green]Your report has been sent, thank you for your contribution.")
                        Mono.empty<Void>()
                    }
                    .subscribe()
            },
            MenuOption("[orange]No") { it.back(2) },
            MenuOption("[red]Abort") { it.closeAll() },
        )
    }

    val reportDetailInterface = TextInputInterface.create(plugin)
    reportDetailInterface.addTransformer { _, pane ->
        pane.title = "Report (3/4)"
        pane.description = "Enter the details of your report, \nclick on cancel if you don't have any."
        pane.inputAction = BiAction { view, input ->
            view.close()
            view.state[REPORT_DETAIL] = input
            reportConfirmInterface.open(view)
        }
        pane.exitAction = Action { view ->
            view.close()
            view.state.remove(REPORT_DETAIL)
            reportConfirmInterface.open(view)
        }
    }

    val reportReasonInterface = MenuInterface.create(plugin)
    reportReasonInterface.addTransformer { _, pane ->
        pane.title = "Report (2/4)"
        pane.content = "Select the reason of your report"
        for (reason in ReportMessage.Reason.entries) {
            pane.options.addRow(
                MenuOption(reason.name.lowercase().capitalize()) { view ->
                    view.close()
                    view.state[REPORT_REASON] = reason
                    reportDetailInterface.open(view)
                },
            )
        }
        pane.options.addRow(MenuOption("[red]Cancel", View::back))
    }

    val playerListInterface = MenuInterface.create(plugin)
    playerListInterface.addTransformer { _, pane ->
        pane.title = "Report (1/4)"
        pane.content = "Select the player you want to report"
    }
    playerListInterface.addTransformer(
        ListTransformer<Player>().apply {
            elementProvider = Function { view ->
                ArcCollections.mutableList(Groups.player).apply {
                    remove(view.viewer)
                    sortBy(Player::plainName)
                }
            }
            elementRenderer = Function { it.plainName() }
            pageHeight = 8
            choiceAction = BiAction { view, player ->
                view.close()
                view.state[REPORT_PLAYER] = player
                reportReasonInterface.open(view)
            }
        },
    )

    return playerListInterface
}
