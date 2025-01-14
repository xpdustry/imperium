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
package com.xpdustry.imperium.mindustry.world

import arc.Events
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.menu.ListTransformer
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.misc.asList
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.id
import com.xpdustry.imperium.mindustry.misc.key
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.translation.LIGHT_GRAY
import com.xpdustry.imperium.mindustry.translation.difficulty_name
import com.xpdustry.imperium.mindustry.translation.gui_back
import com.xpdustry.imperium.mindustry.translation.selected
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.maps.Map

class RockTheVoteCommand(instances: InstanceManager) :
    AbstractVoteCommand<RockTheVoteCommand.MapSelector>(instances.get(), "RTV", 1.minutes),
    ImperiumApplication.Listener {

    private val maps = instances.get<MindustryMapManager>()
    private val menu =
        MenuManager.create(plugin).apply {
            addTransformer { (pane) ->
                pane.title = text("Rock the vote")
                pane.grid.addRow(MenuOption.of("Set the difficulty", Action.none()))
            }

            addTransformer(
                ListTransformer<MindustryMap.Difficulty>()
                    .setProvider { MindustryMap.Difficulty.entries }
                    .setRenderer { ctx, difficulty ->
                        selected(difficulty_name(difficulty), ctx.state[DIFFICULTY] == difficulty)
                    }
                    .setWidth(2)
                    .setRenderNavigation(false)
                    .setHeight(Int.MAX_VALUE)
                    .setChoiceAction { window, difficulty ->
                        if (window.state[DIFFICULTY] == difficulty) {
                            window.state.remove(DIFFICULTY)
                        } else {
                            window.state[DIFFICULTY] = difficulty
                        }
                        window.show()
                    }
            )

            addTransformer { (pane) -> pane.grid.addRow(MenuOption.of("Select a map", Action.none())) }

            addTransformer { (pane, state) ->
                val difficulty = state[DIFFICULTY]
                if (difficulty == null || state[MAPS]!!.any { it.difficulty == difficulty }) {
                    val selector =
                        if (state[DIFFICULTY] == null) {
                            MapSelector.Random
                        } else {
                            MapSelector.Difficulty(state[DIFFICULTY]!!)
                        }
                    pane.grid.addRow(
                        MenuOption.of(
                            text("Random", ComponentColor.YELLOW),
                            Action.hideAll().then { onVoteSessionStart(it.viewer, manager.session, selector) },
                        )
                    )
                } else {
                    pane.grid.addRow(MenuOption.of(text("Random", LIGHT_GRAY), Action.none()))
                }
            }

            addTransformer(
                ListTransformer<MapWithDifficulty>()
                    .setProvider { ctx ->
                        val difficulty = ctx.state[DIFFICULTY]
                        ctx.state[MAPS]!!.filter { difficulty == null || it.difficulty == difficulty }
                    }
                    .setRenderer { it -> Distributor.get().mindustryComponentDecoder.decode(it.map.name()) }
                    .setFillEmptySpace(true)
                    .setChoiceAction(
                        BiAction.from<MapWithDifficulty>(Action.hideAll()).then { window, map ->
                            onVoteSessionStart(window.viewer, manager.session, MapSelector.Specific(map.map))
                        }
                    )
            )

            addTransformer { (pane) -> pane.grid.addRow(MenuOption.of(gui_back(), Action.hideAll())) }
        }

    @ImperiumCommand(["rtv"])
    @ClientSide
    fun onRtvCommand(sender: CommandSender) {
        val window = menu.create(sender.player)
        val list = Vars.maps.customMaps().asList()
        ImperiumScope.MAIN.launch {
            val with =
                list.map { map ->
                    MapWithDifficulty(map, maps.getMapStats(map.id ?: -1)?.difficulty ?: MindustryMap.Difficulty.NORMAL)
                }
            runMindustryThread {
                window.state[MAPS] = with
                window.show()
            }
        }
    }

    @ImperiumCommand(["rtv", "y"])
    @ClientSide
    fun onRtvYesCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.YES)
    }

    @ImperiumCommand(["rtv", "n"])
    @ClientSide
    fun onRtvNoCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.NO)
    }

    @ImperiumCommand(["rtv", "cancel|c"], Rank.MODERATOR)
    @ClientSide
    fun onRtvCancelCommand(sender: CommandSender) {
        onPlayerCancel(sender.player, manager.session)
    }

    @ImperiumCommand(["rtv", "force|f"], Rank.MODERATOR)
    @ClientSide
    fun onRtvForceCommand(sender: CommandSender) {
        onPlayerForceSuccess(sender.player, manager.session)
    }

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<MapSelector>) {
        when (val selector = session.objective) {
            is MapSelector.Specific -> Vars.maps.setNextMapOverride(selector.map)
            is MapSelector.Random -> Unit
            is MapSelector.Difficulty -> {
                val result =
                    Vars.maps.customMaps().asList().filter { map ->
                        val difficulty = maps.getMapStats(map.id ?: -1) ?: MindustryMap.Difficulty.NORMAL
                        difficulty == selector.difficulty
                    }
                if (result.isNotEmpty()) {
                    Vars.maps.setNextMapOverride(result.random())
                }
            }
        }
        Events.fire(EventType.GameOverEvent(Team.derelict))
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<MapSelector>): String =
        "[white]Type [accent]/rtv y[] to vote to change the map to [accent]${session.objective.name()}[white]."

    private fun MapSelector.name(): String =
        when (this) {
            is MapSelector.Specific -> map.name()
            is MapSelector.Random -> "a random map"
            is MapSelector.Difficulty -> "a random ${difficulty.name.lowercase()} map"
        }

    data class MapWithDifficulty(val map: Map, val difficulty: MindustryMap.Difficulty)

    companion object {
        private val DIFFICULTY = key<MindustryMap.Difficulty>("difficulty")
        private val MAPS = key<List<MapWithDifficulty>>("maps")
    }

    sealed interface MapSelector {
        data class Specific(val map: Map) : MapSelector

        data object Random : MapSelector

        data class Difficulty(val difficulty: MindustryMap.Difficulty) : MapSelector
    }
}
