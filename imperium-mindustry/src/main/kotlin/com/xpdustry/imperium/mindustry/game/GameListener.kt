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

import com.xpdustry.distributor.annotation.EventHandler
import com.xpdustry.distributor.util.Priority
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.version.MindustryVersion
import com.xpdustry.imperium.mindustry.misc.getMindustryVersion
import com.xpdustry.imperium.mindustry.misc.playtime
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.snowflake
import com.xpdustry.imperium.mindustry.misc.start
import java.time.Instant
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.core.GameState
import mindustry.game.EventType
import mindustry.io.SaveIO
import mindustry.net.Administration

class GameListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val maps = instances.get<MindustryMapManager>()
    private val autoSave = Vars.saveDirectory.child("auto-save-imperium.${Vars.saveExtension}")
    private val logger by LoggerDelegate()

    override fun onImperiumInit() {
        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(5.seconds)
                if (Vars.state.state != GameState.State.playing || Vars.state.gameOver) continue
                runMindustryThread { Vars.state.map.playtime += 5.seconds }
            }
        }

        // TODO Add better auto-save for anti-grief ?
        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(1.minutes)
                runMindustryThread {
                    if (Vars.state.state == GameState.State.playing &&
                        Administration.Config.autosave.bool() &&
                        getMindustryVersion().type != MindustryVersion.Type.BLEEDING_EDGE) {
                        SaveIO.save(autoSave)
                        logger.debug("Saved current game")
                    }
                }
            }
        }
    }

    @EventHandler(priority = Priority.LOW)
    internal fun onServerLoadEvent(event: EventType.ServerLoadEvent) {
        if (Vars.state.state == GameState.State.menu &&
            Administration.Config.autosave.bool() &&
            autoSave.exists()) {
            // TODO Use MapLoader ?
            SaveIO.load(autoSave)
            Vars.state.set(GameState.State.playing)
            Vars.netServer.openServer()
            logger.info("Loaded Imperium AutoSave")
        }
    }

    @EventHandler
    internal fun onGameBeginEvent(event: MenuToPlayEvent) {
        if (Vars.state.map.start == null) {
            Vars.state.map.start = Instant.now()
        }
    }

    // TODO Notify players when they beat the map world record
    @EventHandler
    internal fun onGameOverEvent(event: EventType.GameOverEvent) {
        val playtime = Vars.state.map.playtime
        val stats = Vars.state.stats
        val waves = Vars.state.wave
        val start = Vars.state.map.start ?: Instant.now()
        val snowflake = Vars.state.map.snowflake ?: return
        if (playtime < 1.minutes) return
        ImperiumScope.MAIN.launch {
            maps.addMapGame(
                map = snowflake,
                start = start,
                playtime = playtime,
                unitsCreated = stats.unitsCreated,
                ennemiesKilled = stats.enemyUnitsDestroyed,
                wavesLasted = waves.coerceAtLeast(0),
                buildingsConstructed = stats.buildingsBuilt,
                buildingsDeconstructed = stats.buildingsDeconstructed,
                buildingsDestroyed = stats.buildingsDestroyed,
                winner = event.winner.id.toUByte())
        }
        Vars.state.map.playtime = ZERO
        Vars.state.map.start = null
    }
}
