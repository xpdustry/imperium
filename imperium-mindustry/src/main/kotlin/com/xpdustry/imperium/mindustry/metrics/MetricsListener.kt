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
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.flex.message.FlexPlayerChatEvent
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.metrics.Counter
import com.xpdustry.imperium.common.metrics.GaugeMetric
import com.xpdustry.imperium.common.metrics.Metric
import com.xpdustry.imperium.common.metrics.MetricsRegistry
import com.xpdustry.imperium.common.metrics.SystemMetricCollector
import com.xpdustry.imperium.common.metrics.UniqueCounter
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import java.net.InetAddress
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team

class MetricsListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val metrics = instances.get<MetricsRegistry>()
    private val users = instances.get<UserManager>()
    private val joinCounter = Counter("mindustry_events_join_total")
    private val quitCounter = Counter("mindustry_events_quit_total")
    private val chatCounter = Counter("mindustry_events_chat_total")

    private val newPlayersCounter = Counter("mindustry_players_new")
    private val uniPlayersCounter = UniqueCounter<InetAddress>("mindustry_players_unique")
    private val retPlayersCounter = Counter("mindustry_players_retained")

    override fun onImperiumInit() {
        metrics.register(SystemMetricCollector())
        metrics.register(joinCounter)
        metrics.register(quitCounter)
        metrics.register(chatCounter)
        metrics.register(newPlayersCounter)
        metrics.register(uniPlayersCounter)
        metrics.register(retPlayersCounter)
        metrics.register { runMindustryThread { collectActiveData() } }
    }

    @EventHandler(priority = Priority.LOW)
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        ImperiumScope.MAIN.launch {
            uniPlayersCounter += event.player.con.address.toInetAddress()
            joinCounter.inc()

            val id = event.player.id()
            if ((users.findByUuid(event.player.uuid())?.timesJoined ?: 0) == 1) {
                newPlayersCounter.inc()
                delay(5.minutes)
                runMindustryThread {
                    if (Entities.findPlayerByID(id)?.con?.isConnected == true) {
                        retPlayersCounter.inc()
                    }
                }
            }
        }

    @EventHandler
    fun onPlayerLeave(event: EventType.PlayerLeave) {
        quitCounter.inc()
    }

    @EventHandler
    fun onPlayerChat(event: FlexPlayerChatEvent) {
        chatCounter.inc()
    }

    private fun collectActiveData(): List<Metric> {
        val result = mutableListOf<Metric>()
        result += GaugeMetric("mindustry_players_max", Vars.netServer.admins.playerLimit)
        Entities.getPlayers()
            .groupingBy { it.team() }
            .eachCount()
            .forEach { (team, count) ->
                result += GaugeMetric("mindustry_players_count", count, labels = mapOf("team" to team.name))
            }
        Team.all.forEach { team ->
            if (!team.active()) return@forEach
            result += GaugeMetric("mindustry_buildings_count", team.data().buildings.size, mapOf("team" to team.name))
            team
                .data()
                .units
                .groupingBy { it.type() }
                .eachCount()
                .forEach { (type, count) ->
                    result +=
                        GaugeMetric("mindustry_units_count", count, mapOf("type" to type.name, "team" to team.name))
                }
        }
        result += GaugeMetric("mindustry_tps", Core.graphics.framesPerSecond)
        result += GaugeMetric("mindustry_game_waves", Vars.state.wave)
        return result
    }
}
