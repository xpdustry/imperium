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
package com.xpdustry.imperium.mindustry.metrics

import arc.Core
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.flex.message.FlexPlayerChatEvent
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.metrics.Counter
import com.xpdustry.imperium.common.metrics.GaugeMetric
import com.xpdustry.imperium.common.metrics.Metric
import com.xpdustry.imperium.common.metrics.MetricsRegistry
import com.xpdustry.imperium.common.metrics.SystemMetricCollector
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team

class MetricsListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val metrics = instances.get<MetricsRegistry>()
    private val joinCounter = Counter("mindustry_events_join_total")
    private val quitCounter = Counter("mindustry_events_quit_total")
    private val chatCounter = Counter("mindustry_events_chat_total")

    override fun onImperiumInit() {
        metrics.register(SystemMetricCollector())
        metrics.register(joinCounter)
        metrics.register(quitCounter)
        metrics.register(chatCounter)
        metrics.register { runMindustryThread { collectLiveServerData() } }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        joinCounter.inc()
    }

    @EventHandler
    fun onPlayerLeave(event: EventType.PlayerLeave) {
        quitCounter.inc()
    }

    @EventHandler
    fun onPlayerChat(event: FlexPlayerChatEvent) {
        chatCounter.inc()
    }

    private fun collectLiveServerData(): List<Metric> {
        val result = mutableListOf<Metric>()
        result += GaugeMetric("mindustry_players_max", Vars.netServer.admins.playerLimit)
        Entities.getPlayers()
            .groupingBy { it.team() }
            .eachCount()
            .forEach { (team, count) ->
                result +=
                    GaugeMetric(
                        "mindustry_players_count", count, labels = mapOf("team" to team.name))
            }
        Team.all.forEach { team ->
            if (!team.active()) return@forEach
            result +=
                GaugeMetric(
                    "mindustry_buildings_count",
                    team.data().buildings.size,
                    mapOf("team" to team.name))
            team
                .data()
                .units
                .groupingBy { it.type() }
                .eachCount()
                .forEach { (type, count) ->
                    result +=
                        GaugeMetric(
                            "mindustry_units_count",
                            count,
                            mapOf("type" to type.name, "team" to team.name))
                }
        }
        result += GaugeMetric("mindustry_tps", Core.graphics.framesPerSecond)
        result += GaugeMetric("mindustry_game_waves", Vars.state.wave)
        return result
    }
}
