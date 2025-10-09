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

import arc.math.geom.Point2
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.translation.player_afk
import com.xpdustry.imperium.mindustry.translation.player_afk_kick
import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Player

interface AfkManager {
    fun isPlayerAfk(player: Player): Boolean
}

class AfkListener @Inject constructor(private val config: ImperiumConfig, plugin: MindustryPlugin) :
    AfkManager, LifecycleListener {
    private val lastActivity = PlayerMap<Instant>(plugin)
    private val lastPosition = PlayerMap<Int>(plugin)
    private val notified = PlayerMap<Unit>(plugin)

    override fun onImperiumInit() {
        Vars.netServer.admins.addActionFilter { action ->
            onDisturbingThePeace(action.player)
            true
        }

        Vars.netServer.admins.addChatFilter { player, message ->
            if (message.isNotBlank()) onDisturbingThePeace(player)
            message
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        onDisturbingThePeace(event.player)
    }

    @TaskHandler(interval = 15, unit = MindustryTimeUnit.SECONDS)
    fun onPlayerAfkUpdate() {
        Entities.getPlayers().forEach { player ->
            val new = Point2.pack(player.tileX(), player.tileY())
            val old = lastPosition.set(player, new)
            if (old != new) {
                onDisturbingThePeace(player)
            }

            val duration = getAfkDuration(player).toKotlinDuration()
            when {
                duration >= config.mindustry.afkDelay -> {
                    if (notified.set(player, Unit) != Unit) player.asAudience.sendMessage(player_afk(enabled = true))
                }
                duration >= config.mindustry.afkKickDelay -> {
                    player.asAudience.kick(player_afk_kick(), Duration.ZERO)
                }
            }
        }
    }

    override fun isPlayerAfk(player: Player): Boolean {
        return getAfkDuration(player).toKotlinDuration() >= config.mindustry.afkDelay
    }

    private fun getAfkDuration(player: Player): Duration {
        return lastActivity[player]?.let { Duration.between(it, Instant.now()) } ?: Duration.ZERO
    }

    // PERSONA!!
    private fun onDisturbingThePeace(player: Player) {
        if (isPlayerAfk(player)) {
            player.asAudience.sendMessage(player_afk(enabled = false))
        }
        notified.remove(player)
        lastActivity[player] = Instant.now()
    }
}
