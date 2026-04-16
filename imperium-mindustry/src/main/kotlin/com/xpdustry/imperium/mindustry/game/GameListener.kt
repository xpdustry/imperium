// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.version.MindustryVersion
import com.xpdustry.imperium.mindustry.map.MapLoader
import com.xpdustry.imperium.mindustry.misc.getMindustryVersion
import com.xpdustry.imperium.mindustry.misc.id
import com.xpdustry.imperium.mindustry.misc.playtime
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
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

@Inject
class GameListener constructor(private val config: ImperiumConfig, private val maps: MindustryMapManager) :
    ImperiumApplication.Listener {
    private val autoSave = Vars.saveDirectory.child("auto_imperium.${Vars.saveExtension}")
    private val logger by LoggerDelegate()

    override fun onImperiumInit() {
        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(5.seconds)
                if (Vars.state.state != GameState.State.playing || Vars.state.gameOver) continue
                runMindustryThread { Vars.state.map.playtime += 5.seconds }
            }
        }

        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(1.minutes)
                runMindustryThread {
                    if (
                        Vars.state.state == GameState.State.playing &&
                            Administration.Config.autosave.bool() &&
                            getMindustryVersion().type != MindustryVersion.Type.BLEEDING_EDGE
                    ) {
                        SaveIO.save(autoSave)
                        logger.debug("Saved current game")
                    }
                }
            }
        }

        if (Vars.state.state == GameState.State.menu && Administration.Config.autosave.bool() && autoSave.exists()) {
            MapLoader().use { loader -> loader.load(autoSave.file()) }
            logger.info("Loaded Imperium AutoSave")
        }
    }

    @EventHandler
    internal fun onGameBeginEvent(event: MenuToPlayEvent) {
        if (Vars.state.map.start == null) {
            Vars.state.map.start = Instant.now()
        }
    }

    @EventHandler
    internal fun onGameOverEvent(event: EventType.GameOverEvent) {
        val playtime = Vars.state.map.playtime
        val stats = Vars.state.stats
        val waves = Vars.state.wave
        val start = Vars.state.map.start ?: Instant.now()
        val mapId = Vars.state.map.id ?: return
        if (playtime < 1.minutes) return
        ImperiumScope.MAIN.launch {
            maps.addMapGame(
                mapId,
                MindustryMap.PlayThrough.Data(
                    server = config.server.name,
                    start = start,
                    playtime = playtime,
                    unitsCreated = stats.unitsCreated,
                    ennemiesKilled = stats.enemyUnitsDestroyed,
                    wavesLasted = waves.coerceAtLeast(0),
                    buildingsConstructed = stats.buildingsBuilt,
                    buildingsDeconstructed = stats.buildingsDeconstructed,
                    buildingsDestroyed = stats.buildingsDestroyed,
                    winner = event.winner.id.toUByte(),
                ),
            )
        }
        Vars.state.map.playtime = ZERO
        Vars.state.map.start = null
    }
}
