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
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.audience.PlayerAudience
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.flex.FlexAPI
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.translation.player_afk
import com.xpdustry.imperium.mindustry.translation.player_afk_kick
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.future.future
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Player

// TODO: Make chatting count towards player activity

interface AfkManager {
    fun isPlayerAfk(player: Player): Boolean

    fun getAfkPlayerCount(): Int
}

class AfkListener(instances: InstanceManager) : AfkManager, ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    val afkPlayers = mutableMapOf<Player, Instant>()
    val playerActionTimer = mutableMapOf<Player, Instant>()

    // action filter is easiest way to track player actions
    override fun onImperiumInit() {
        Vars.netServer.admins.addActionFilter { action ->
            playerActionTimer[action.player] = Instant.now()
            val removed: Boolean = afkPlayers.remove(action.player) != null
            if (removed) (action.player as PlayerAudience).sendMessage(player_afk(removed = true))
            true
        }

        // I think this is hacky, but it's better than nothing
        FlexAPI.get().messages.register("player_afk", Priority.NORMAL) { ctx ->
            ImperiumScope.MAIN.future {
                val player = ctx.sender as? PlayerAudience ?: return@future ctx.message
                playerActionTimer[player.player] = Instant.now()
                val removed: Boolean = afkPlayers.remove(player.player) != null
                if (removed) player.sendMessage(player_afk(removed = true))
                ctx.message // we dont filter nor block
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        afkPlayers.remove(event.player)
        playerActionTimer[event.player] = Instant.now()
    }

    @EventHandler
    fun onPlayerLeave(event: EventType.PlayerLeave) {
        afkPlayers.remove(event.player)
        playerActionTimer.remove(event.player)
    }

    @TaskHandler(interval = 5L, unit = MindustryTimeUnit.SECONDS)
    fun onPlayerAfk() {
        for ((player, lastActionTime) in playerActionTimer) {
            if (lastActionTime.isBefore(Instant.now().minusSeconds(config.mindustry.afkDelay.inWholeSeconds))) {
                playerActionTimer.remove(player)
                if (!config.mindustry.kickAfkPlayers)
                    Distributor.get().audienceProvider.getPlayer(player).sendMessage(player_afk(removed = false))
                else {
                    Distributor.get().audienceProvider.getPlayer(player).kick(player_afk_kick(), Duration.ZERO)
                    continue
                }
                afkPlayers[player] = Instant.now()
            }
        }
    }

    override fun isPlayerAfk(player: Player): Boolean {
        return afkPlayers.containsKey(player)
    }

    // TODO: Make this applied by default to the vote requirements
    // Current implementation is manually adding it to every vote file
    override fun getAfkPlayerCount(): Int = afkPlayers.size
}
