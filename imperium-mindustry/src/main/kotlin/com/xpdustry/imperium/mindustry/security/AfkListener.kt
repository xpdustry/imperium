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
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.translation.is_player_afk
import com.xpdustry.imperium.mindustry.translation.player_afk_announcement
import com.xpdustry.imperium.mindustry.translation.player_afk_kick
import java.time.Duration
import java.time.Instant
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Player

interface AfkManager {
    fun isPlayerAfk(player: Player): Boolean
}

class AfkListener(private val config: ImperiumConfig, plugin: MindustryPlugin) :
    AfkManager, ImperiumApplication.Listener {
    private val lastActivity = PlayerMap<Instant>(plugin)
    private val notified = PlayerMap<Unit>(plugin)

    override fun onImperiumInit() {
        // TODO: TapEvent is not covered here but it is a valid way to show a player is there however how to detect if
        // its just a miss click? Do TapEvents occur if the window isnt focused?
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
        for (player in Entities.getPlayers()) {
            checkAfkStatus(player)
        }
    }

    override fun isPlayerAfk(player: Player): Boolean {
        return getAfkDuration(player).toKotlinDuration() >= config.mindustry.afkDelay
    }

    private fun checkAfkStatus(player: Player) {
        val duration = getAfkDuration(player).toKotlinDuration()

        when {
            duration >= config.mindustry.afkKickDelay -> {
                player.asAudience.kick(player_afk_kick(), Duration.ZERO)
            }
            duration >= config.mindustry.afkDelay -> {
                if (notified.set(player, Unit) != Unit) {
                    Distributor.get()
                        .audienceProvider
                        .players
                        .sendMessage(player_afk_announcement(true, player.plainName()))
                }
            }
        }
    }

    private fun getAfkDuration(player: Player): Duration {
        return lastActivity[player]?.let { Duration.between(it, Instant.now()) } ?: Duration.ZERO
    }

    // PERSONA!!
    private fun onDisturbingThePeace(player: Player) {
        if (isPlayerAfk(player)) {
            Distributor.get().audienceProvider.players.sendMessage(player_afk_announcement(false, player.plainName()))
        }
        notified.remove(player)
        lastActivity[player] = Instant.now()
    }

    @ImperiumCommand(["afk"])
    @ClientSide
    @ServerSide
    fun isAfkCommand(sender: CommandSender, target: Player? = null) {
        if (target == null) {
            if (sender.isPlayer) {
                val afkInstant = Instant.now().minus((config.mindustry.afkDelay).toJavaDuration())
                lastActivity[sender.player] = afkInstant
                checkAfkStatus(sender.player)
            } else sender.error("Console must specify a player. You can't be afk as console.")
        } else {
            val isAfk = isPlayerAfk(target)
            sender.reply(is_player_afk(isAfk, target.plainName()))
        }
    }
}
