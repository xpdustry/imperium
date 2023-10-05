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
package com.xpdustry.imperium.mindustry.world

import arc.Events
import cloud.commandframework.kotlin.extension.buildAndRegister
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.command.SimpleVoteManager
import com.xpdustry.imperium.mindustry.command.VoteManager
import com.xpdustry.imperium.mindustry.misc.registerCopy
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.menu.ListTransformer
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import fr.xpdustry.distributor.api.util.ArcCollections
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.maps.Map
import kotlin.time.Duration.Companion.minutes

class RockTheVoteCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val clientCommandManager = instances.get<ImperiumPluginCommandManager>("client")
    private val plugin = instances.get<MindustryPlugin>()

    private val manager = SimpleVoteManager<Map?>(
        plugin = instances.get(),
        duration = 1.minutes,
        finished = {
            if (it.status != VoteManager.Session.Status.SUCCESS) {
                Call.sendMessage("[orange]The RTV vote failed.")
            } else {
                Call.sendMessage("[orange]The RTV vote passed. The map will be changed.")
                if (it.target != null) Vars.maps.setNextMapOverride(it.target)
                Events.fire(EventType.GameOverEvent(Team.derelict))
            }
        },
    )

    override fun onImperiumInit() {
        val mapListInterface = MenuInterface.create(plugin)
        mapListInterface.addTransformer(
            ListTransformer(
                provider = { ArcCollections.immutableList(Vars.maps.customMaps()) },
                renderer = { it.name() },
                onChoice = { view, map -> start(view, map) },
                fill = true,
            ),
        )
        mapListInterface.addTransformer { _, pane ->
            pane.title = "Choose a map"
            pane.options.addRow(MenuOption("[yellow]Random") { view -> start(view, null) })
        }

        clientCommandManager.buildAndRegister("rtv") {
            handler { ctx -> mapListInterface.open(ctx.sender.player) }

            registerCopy("yes", aliases = arrayOf("y")) {
                commandDescription("Vote yes to change the map.")
                handler { vote(it.sender.player, manager.session, true) }
            }

            registerCopy("no", aliases = arrayOf("n")) {
                commandDescription("Vote no to change the map.")
                handler { vote(it.sender.player, manager.session, false) }
            }
        }
    }

    private fun start(view: View, map: Map?) {
        view.closeAll()
        if (manager.session == null) {
            manager.start(view.viewer, true, map)
            Call.sendMessage("[orange]A vote to change the map has started. Type [accent]/rtv y[] to vote.")
        } else {
            view.viewer.sendMessage("[orange]A RTV is already in progress.")
        }
    }

    private fun vote(player: Player, session: VoteManager.Session<Map?>?, value: Boolean) {
        if (session == null) {
            player.sendMessage("[orange]There is no RTV in progress.")
        } else if (session.getVote(player) != null) {
            player.sendMessage("[orange]You have already voted.")
        } else {
            Call.sendMessage("${player.name()} [orange] has voted to${if (value) " " else " not "}change the map.")
            session.setVote(player, value)
        }
    }
}
