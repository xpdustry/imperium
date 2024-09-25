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

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.id
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.ui.action.Action
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.gen.Call

class RatingListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val maps = instances.get<MindustryMapManager>()
    private val ratingInterface =
        MenuInterface.create(instances.get()).apply {
            addTransformer { view, pane ->
                pane.title = "Rate the map"

                pane.options.addRow(MenuOption("How would you score this map ?"))
                pane.options.addRow(
                    (1..5).map { score ->
                        val display =
                            if (view.state[SCORE] == score) "> $score <" else score.toString()
                        MenuOption(display, Action.open { it[SCORE] = score })
                    })

                pane.options.addRow(MenuOption("How difficult is the map ?"))
                MindustryMap.Difficulty.entries.forEach { difficulty ->
                    var display = difficulty.name.lowercase()
                    if (view.state[DIFFICULTY] == difficulty) {
                        display = "> $display <"
                    }
                    display = "[${difficulty.color}]$display"
                    pane.options.addRow(
                        MenuOption(display, Action.open { it[DIFFICULTY] = difficulty }))
                }

                pane.options.addRow(
                    MenuOption("Cancel", Action.close()),
                    MenuOption("Submit") { v ->
                        ImperiumScope.MAIN.launch {
                            val user = users.getByIdentity(view.viewer.identity).id
                            maps.saveRating(
                                Vars.state.map.id!!, user, v.state[SCORE]!!, v.state[DIFFICULTY]!!)
                            runMindustryThread {
                                v.back()
                                Call.infoMessage(
                                    "Your rating has been submitted, thanks for your feedback.")
                            }
                        }
                    })
            }
        }

    @ImperiumCommand(["rate"])
    @ClientSide
    suspend fun onRateCommand(sender: CommandSender) {
        val user = users.getByIdentity(sender.player.identity).id
        val map = Vars.state.map.id
        if (map == null || maps.findMapById(map) == null) {
            runMindustryThread { sender.error("This map can't be rated.") }
        } else {
            val rating = maps.findRatingByMapAndUser(map, user)
            runMindustryThread {
                ratingInterface.open(sender.player) {
                    it[SCORE] = rating?.score ?: 3
                    it[DIFFICULTY] = rating?.difficulty ?: MindustryMap.Difficulty.NORMAL
                }
            }
        }
    }

    private val MindustryMap.Difficulty.color: String
        get() =
            when (this) {
                MindustryMap.Difficulty.EASY -> "lime"
                MindustryMap.Difficulty.NORMAL -> "accent"
                MindustryMap.Difficulty.HARD -> "orange"
                MindustryMap.Difficulty.EXPERT -> "scarlet"
            }

    companion object {
        private val SCORE = stateKey<Int>("score")
        private val DIFFICULTY = stateKey<MindustryMap.Difficulty>("difficulty")
    }
}
