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

import arc.Core
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.popup.PopupManager
import com.xpdustry.distributor.api.gui.popup.PopupPane.AlignementX
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.collection.LimitedList
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.toHexString
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.asList
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.component3
import com.xpdustry.imperium.mindustry.misc.getItemIcon
import java.awt.Color
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Player
import mindustry.type.Item
import mindustry.world.blocks.storage.CoreBlock.CoreBuild

class ResourceHudListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val teams = mutableMapOf<Team, ResourceTracker>()
    private val config = instances.get<ImperiumConfig>()
    private val popup = PopupManager.create(instances.get())

    init {
        popup.addTransformer { (pane, _, viewer) ->
            pane.alignementX = AlignementX.RIGHT
            pane.setContent(
                buildString {
                    val tracker = teams[viewer.team()] ?: return@buildString
                    val map = tracker.change.filterValues { it != 0 }
                    if (map.isEmpty()) {
                        append("[gray]No changes")
                        return@buildString
                    }
                    append("Rate of Change:")
                    for ((item, change) in map) {
                        val rate = tracker.rate[item]!!
                        val color = getColor(rate)
                        val sign = if (rate > 0F) "+" else "-"
                        append("\n[white]")
                        append(getItemIcon(item))
                        append(" ")
                        append("[${color.toHexString()}]")
                        append(sign)
                        append("[white]")
                        append(formatChange(change))
                        append(" ")
                        append("[${color.toHexString()}]")
                        append(sign)
                        append(String.format("%.2f", rate.absoluteValue))
                        append("[white]")
                        append("%")
                    }
                    append("\nEvery minute")
                }
            )
        }
    }

    @TaskHandler(interval = 1L, unit = MindustryTimeUnit.SECONDS)
    fun onHUDUpdate() {
        if (!config.mindustry.world.displayResourceTracker || !Vars.state.isPlaying) return
        updateResourceTrackers()
        ImperiumScope.MAIN.launch {
            for (player in Entities.getPlayersAsync()) {
                val enabled = users.getSetting(player.uuid(), User.Setting.RESOURCE_HUD)
                Core.app.post {
                    if (enabled) {
                        if (getActiveWindow(player) == null) popup.create(player).show()
                    } else {
                        getActiveWindow(player)?.hide()
                    }
                }
            }
        }
    }

    @EventHandler
    fun onMenuToPlayEvent(event: MenuToPlayEvent) {
        teams.clear()
    }

    private fun getActiveWindow(player: Player): Window? {
        return popup.activeWindows.firstOrNull { it.viewer == player }
    }

    private fun updateResourceTrackers() {
        for (team in Team.all) {
            if (!team.active()) continue
            for (item in Vars.content.items().asList()) {
                val tracker = teams.getOrPut(team, ::ResourceTracker)
                val list = tracker.items.getOrPut(item) { LimitedList(RECORDED_SECONDS) }
                list += team.items().get(item)
                val change = getAverageChange(list) * PROJECTED_SECONDS
                tracker.change[item] = change
                tracker.rate[item] = (change / max(team.cores().sum(CoreBuild::storageCapacity).toFloat(), 1F)) * 100F
            }
        }
    }

    private fun formatChange(change: Int): String {
        val value = change.absoluteValue
        if (value > 999_999) {
            return "${String.format("%.1f", value / 1_000_000F)}[gray]M"
        } else if (value > 999) {
            return "${String.format("%.1f", value / 1_000F)}[gray]K"
        }
        return value.toString()
    }

    private fun getColor(rate: Float): Color =
        Color(arc.graphics.Color.HSVtoRGB(if (rate > 0F) 120F else 0F, min(abs(rate) * 200F, 100F), 100F).rgb888())

    private fun getAverageChange(list: List<Int>): Int {
        if (list.size < 2) return 0
        var sum = 0
        for (i in 1 until list.size) {
            sum += list[i] - list[i - 1]
        }
        return sum / (list.size - 1)
    }

    data class ResourceTracker(
        val items: MutableMap<Item, LimitedList<Int>> = mutableMapOf(),
        val change: MutableMap<Item, Int> = mutableMapOf(),
        val rate: MutableMap<Item, Float> = mutableMapOf(),
    )

    companion object {
        private const val RECORDED_SECONDS = 10
        private const val PROJECTED_SECONDS = 60
    }
}
