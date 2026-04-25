// SPDX-License-Identifier: GPL-3.0-only
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
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.translation.is_player_afk
import com.xpdustry.imperium.mindustry.translation.player_afk_announcement
import com.xpdustry.imperium.mindustry.translation.player_afk_kick
import java.time.Duration as JavaDuration
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Player

interface AfkManager {
    fun isPlayerAfk(player: Player): Boolean
}

@Inject
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

    override fun isPlayerAfk(player: Player): Boolean = getAfkDuration(player) >= config.mindustry.afkDelay

    private fun checkAfkStatus(player: Player) {
        val duration = getAfkDuration(player)

        when {
            duration >= config.mindustry.afkKickDelay -> {
                player.asAudience.kick(player_afk_kick(), JavaDuration.ZERO)
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

    private fun getAfkDuration(player: Player): Duration =
        lastActivity[player]?.let { Clock.System.now() - it } ?: Duration.ZERO

    // PERSONA!!
    private fun onDisturbingThePeace(player: Player) {
        if (isPlayerAfk(player)) {
            Distributor.get().audienceProvider.players.sendMessage(player_afk_announcement(false, player.plainName()))
        }
        notified.remove(player)
        lastActivity[player] = Clock.System.now()
    }

    @ImperiumCommand(["afk"])
    @ClientSide
    @ServerSide
    fun isAfkCommand(sender: CommandSender, target: Player? = null) {
        if (target == null) {
            if (sender.isPlayer) {
                val afkInstant = Clock.System.now() - config.mindustry.afkDelay
                lastActivity[sender.player] = afkInstant
                checkAfkStatus(sender.player)
            } else sender.error("Console must specify a player. You can't be afk as console.")
        } else {
            val isAfk = isPlayerAfk(target)
            sender.reply(is_player_afk(isAfk, target.plainName()))
        }
    }
}
