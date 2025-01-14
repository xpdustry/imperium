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
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.NumberComponent.number
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.CoroutineAction
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.HideAllAndAnnounceAction
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.id
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.key
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.translation.difficulty_name
import com.xpdustry.imperium.mindustry.translation.gui_close
import com.xpdustry.imperium.mindustry.translation.gui_rate_map_content_difficulty_title
import com.xpdustry.imperium.mindustry.translation.gui_rate_map_content_score_title
import com.xpdustry.imperium.mindustry.translation.gui_rate_map_failure
import com.xpdustry.imperium.mindustry.translation.gui_rate_map_success
import com.xpdustry.imperium.mindustry.translation.gui_rate_map_title
import com.xpdustry.imperium.mindustry.translation.gui_submit
import com.xpdustry.imperium.mindustry.translation.selected
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Player

class RatingListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val maps = instances.get<MindustryMapManager>()
    private val delays = PlayerMap<Long>(instances.get())
    private val menu =
        MenuManager.create(instances.get()).apply {
            addTransformer { (pane, state) ->
                pane.title = gui_rate_map_title()

                pane.grid.addRow(MenuOption.of(gui_rate_map_content_score_title(), Action.none()))
                pane.grid.addRow()
                (1..5).forEach { score ->
                    pane.grid.addOption(
                        MenuOption.of(
                            selected(number(score), state[SCORE] == score),
                            Action.with(SCORE, score).then(Window::show),
                        )
                    )
                }

                pane.grid.addRow(MenuOption.of(gui_rate_map_content_difficulty_title(), Action.none()))
                MindustryMap.Difficulty.entries.forEach { difficulty ->
                    pane.grid.addRow(
                        MenuOption.of(
                            selected(difficulty_name(difficulty), state[DIFFICULTY] == difficulty),
                            Action.with(DIFFICULTY, difficulty).then(Window::show),
                        )
                    )
                }

                pane.grid.addRow(
                    MenuOption.of(gui_close(), Window::hide),
                    MenuOption.of(
                        gui_submit(),
                        CoroutineAction(success = BiAction.from(HideAllAndAnnounceAction(gui_rate_map_success()))) {
                            maps.saveRating(
                                Vars.state.map.id!!,
                                users.getByIdentity(it.viewer.identity).id,
                                it.state[SCORE]!!,
                                it.state[DIFFICULTY]!!,
                            )
                        },
                    ),
                )
            }
        }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        delays[event.player] = System.currentTimeMillis()
    }

    @EventHandler
    fun onReset(event: MenuToPlayEvent) {
        delays.clear()
        val now = System.currentTimeMillis()
        Entities.getPlayers().forEach { player -> delays[player] = now }
    }

    @TaskHandler(delay = 1L, interval = 1L, unit = MindustryTimeUnit.MINUTES)
    fun onRateMapCheck() {
        val now = System.currentTimeMillis()
        delays.entries.toList().forEach { (player, time) ->
            if (now - time > 10.minutes.inWholeMilliseconds) {
                delays.remove(player)
                ImperiumScope.MAIN.launch { tryOpenRatingMenu(player, true) }
            }
        }
    }

    @ImperiumCommand(["rate"])
    @ClientSide
    suspend fun onRateCommand(sender: CommandSender) {
        tryOpenRatingMenu(sender.player, false)
    }

    private suspend fun tryOpenRatingMenu(player: Player, automatic: Boolean) {
        val user = users.getByIdentity(player.identity).id
        val map = Vars.state.map.id
        if (map == null || maps.findMapById(map) == null) {
            if (automatic) return
            runMindustryThread { player.asAudience.sendMessage(gui_rate_map_failure()) }
        } else {
            val rating = maps.findRatingByMapAndUser(map, user)
            if (automatic && rating != null) return
            runMindustryThread {
                val window = menu.create(player)
                window.state[SCORE] = rating?.score ?: 3
                window.state[DIFFICULTY] = rating?.difficulty ?: MindustryMap.Difficulty.NORMAL
                window.show()
            }
        }
    }

    companion object {
        private val SCORE = key("score", Int::class.javaObjectType)
        private val DIFFICULTY = key<MindustryMap.Difficulty>("difficulty")
    }
}
