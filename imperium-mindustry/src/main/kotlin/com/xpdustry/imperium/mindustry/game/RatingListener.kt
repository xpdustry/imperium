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
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.annotation.method.EventHandler
import com.xpdustry.distributor.annotation.method.TaskHandler
import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.distributor.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.asList
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.snowflake
import com.xpdustry.imperium.mindustry.ui.action.Action
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player

class RatingListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val maps = instances.get<MindustryMapManager>()
    private val checks = PlayerMap<Instant>(instances.get())
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
                pane.options.addRow(MenuOption("What is the difficulty of the map ?"))
                MindustryMap.Difficulty.entries.forEach { difficulty ->
                    var display = difficulty.name.lowercase()
                    if (view.state[DIFFICULTY] == difficulty) {
                        display = "> $display <"
                    }
                    display = "[${difficulty.color}]$display"
                    pane.options.addRow(
                        MenuOption(display, Action.open { it[DIFFICULTY] = difficulty }))
                }
                val valid = view.state.contains(SCORE) && view.state.contains(DIFFICULTY)
                pane.options.addRow(
                    MenuOption("Cancel", Action.close()),
                    MenuOption(if (valid) "Submit" else "[gray]Submit") { v ->
                        if (!valid) {
                            v.viewer.sendMessage("You need to select all criteria")
                            return@MenuOption
                        }
                        ImperiumScope.MAIN.launch {
                            val user = users.getByIdentity(view.viewer.identity).snowflake
                            maps.saveRating(
                                Vars.state.map.snowflake!!,
                                user,
                                v.state[SCORE]!!,
                                v.state[DIFFICULTY]!!)
                            runMindustryThread {
                                v.back()
                                Call.sendMessage(
                                    "Your rating has been submitted, thanks for your feedback.")
                            }
                        }
                    })
            }
        }

    @ImperiumCommand(["rate"])
    @ClientSide
    fun onRateCommand(sender: CommandSender) {
        openRatingMenu(sender.player)
    }

    @TaskHandler(interval = 1L, unit = MindustryTimeUnit.MINUTES)
    fun onPeriodicRatingCheck() {
        for (player in Groups.player.asList()) {
            val join = checks[player]
            if (join != null &&
                Duration.between(join, Instant.now()) > 15.minutes.toJavaDuration()) {
                openRatingMenu(player)
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        ImperiumScope.MAIN.launch {
            val rating = getCurrentMapRating(event.player)
            // TODO Add lastUpdate field to rating to allow rating updates when map updates
            if (rating == null) {
                runMindustryThread { checks[event.player] = Instant.now() }
            }
        }
    }

    private fun openRatingMenu(player: Player) {
        checks.remove(player)
        ImperiumScope.MAIN.launch {
            val rating = getCurrentMapRating(player)
            runMindustryThread {
                ratingInterface.open(player) {
                    it[SCORE] = rating?.score ?: 3
                    it[DIFFICULTY] = rating?.difficulty ?: MindustryMap.Difficulty.NORMAL
                }
            }
        }
    }

    private suspend fun getCurrentMapRating(player: Player): MindustryMap.Rating? {
        val user = users.getByIdentity(player.identity).snowflake
        val map = Vars.state.map.snowflake ?: return null
        return maps.findRatingByMapAndUser(map, user)
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
