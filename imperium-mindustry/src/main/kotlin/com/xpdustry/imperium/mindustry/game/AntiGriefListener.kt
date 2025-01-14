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

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.security.RateLimiter
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.history.Historian
import com.xpdustry.imperium.mindustry.history.HistoryEntry
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.security.MarkedPlayerManager
import com.xpdustry.imperium.mindustry.translation.marked_griefer_block
import com.xpdustry.imperium.mindustry.translation.marked_griefer_unit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.world.Block
import mindustry.world.blocks.power.PowerGenerator
import mindustry.world.blocks.storage.StorageBlock

class AntiGriefListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val historian = instances.get<Historian>()
    private val users = instances.get<UserManager>()
    private val isNew = PlayerMap<Boolean>(instances.get())
    private val control = PlayerMap<Int>(instances.get())
    private val deaths = PlayerMap<RateLimiter<Unit>>(instances.get())
    private val marks = instances.get<MarkedPlayerManager>()

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        ImperiumScope.MAIN.launch {
            val info = users.getByIdentity(event.player.identity)
            runMindustryThread { isNew[event.player] = info.timesJoined < 10 }
        }

    @EventHandler
    fun onSuspiciousUnitControl(event: EventType.UnitControlEvent) {
        if (event.unit == null || event.unit.spawnedByCore() || marks.isMarked(event.player)) return
        control[event.player] = event.unit.id
    }

    @EventHandler
    fun onSuspiciousUnitDeath(event: EventType.UnitDestroyEvent) {
        val (player, _) = control.entries.firstOrNull { (_, id) -> id == event.unit.id() } ?: return
        if (marks.isMarked(player) || isNew[player] == false) return
        val limiter = deaths[player] ?: SimpleRateLimiter(if (Vars.state.rules.pvp) 10 else 5, 1.minutes)
        deaths[player] = limiter
        if (limiter.incrementAndCheck(Unit)) return
        Distributor.get().audienceProvider.everyone.sendMessage(marked_griefer_unit(player))
        marks.mark(player)
    }

    @EventHandler
    fun onReset(event: MenuToPlayEvent) {
        deaths.clear()
        control.clear()
    }

    @EventHandler
    fun onSuspiciousBlockDelete(event: EventType.BlockBuildBeginEvent) {
        if (!event.breaking || !event.unit.isPlayer) return
        val player = event.unit.player
        if (isNew[player] != true || marks.isMarked(player)) return
        val history = historian.getHistory(player.uuid())
        val now = System.currentTimeMillis()
        val score =
            history
                .asSequence()
                .filter { (now - it.timestamp.epochSecond).milliseconds < 3.minutes }
                .map {
                    val sign =
                        when (it.type) {
                            HistoryEntry.Type.BREAK,
                            HistoryEntry.Type.BREAKING -> 1F
                            HistoryEntry.Type.PLACE -> -1F
                            else -> return@map 0F
                        }
                    (it.block.size * it.block.size) * it.block.importance * sign
                }
                .sum()
        if (score < config.mindustry.security.griefingThreshold) return
        Distributor.get().audienceProvider.everyone.sendMessage(marked_griefer_block(player))
        marks.mark(player)
    }

    private val Block.importance: Float
        get() =
            when (this) {
                is StorageBlock -> 1.5F
                is PowerGenerator -> 2F
                else -> 1F
            }
}
