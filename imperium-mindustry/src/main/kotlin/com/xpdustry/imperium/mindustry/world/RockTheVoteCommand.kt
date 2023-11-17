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
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.security.permission.Role
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.misc.asList
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.menu.ListTransformer
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import fr.xpdustry.distributor.api.command.sender.CommandSender
import kotlin.time.Duration.Companion.minutes
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.maps.Map

class RockTheVoteCommand(instances: InstanceManager) :
    AbstractVoteCommand<Map?>(instances.get(), "RTV", 1.minutes), ImperiumApplication.Listener {

    private val mapListInterface: Interface =
        MenuInterface.create(plugin).apply {
            addTransformer { _, pane ->
                pane.title = "Choose a map"
                pane.options.addRow(
                    MenuOption("[yellow]Random") { view ->
                        view.closeAll()
                        onVoteSessionStart(view.viewer, manager.session, null)
                    })
            }
            addTransformer(
                ListTransformer(
                    provider = { Vars.maps.customMaps().asList() },
                    renderer = { it.name() },
                    fill = true,
                    onChoice = { view, map ->
                        view.closeAll()
                        onVoteSessionStart(view.viewer, manager.session, map)
                    },
                ),
            )
        }

    @Command(["rtv"])
    @ClientSide
    private fun onRtvCommand(sender: CommandSender) {
        mapListInterface.open(sender.player)
    }

    @Command(["rtv", "y"])
    @ClientSide
    private fun onRtvYesCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.YES)
    }

    @Command(["rtv", "n"])
    @ClientSide
    private fun onRtvNoCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.NO)
    }

    @Command(["rtv", "c"], Role.MODERATOR)
    @ClientSide
    private fun onRtvCancelCommand(sender: CommandSender) {
        onPlayerCancel(sender.player, manager.session)
    }

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<Map?>) {
        runMindustryThread {
            if (session.objective != null) Vars.maps.setNextMapOverride(session.objective)
            Events.fire(EventType.GameOverEvent(Team.derelict))
        }
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<Map?>): String =
        "Type [accent]/rtv y[] to vote to change the map to ${session.objective?.name() ?: "a random one"}."
}
