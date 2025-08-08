package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.translation.player_afk
import com.xpdustry.imperium.mindustry.translation.player_afk_kick
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Player
import java.time.Instant
import java.time.Duration

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
            if (removed) Distributor.get().audienceProvider.getPlayer(action.player).sendMessage(player_afk(removed = true))
            true
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
                if (!config.mindustry.kickAfkPlayers) Distributor.get().audienceProvider.getPlayer(player).sendMessage(player_afk(removed = false))
                else {
                    Distributor.get().audienceProvider.getPlayer(player).kick(player_afk_kick(), Duration.ZERO)
                    continue
                }
                afkPlayers[player] = Instant.now()
            }
        }
    }

    override fun isPlayerAfk(player: Player): Boolean {
        onPlayerAfk() // Ensure 1 player is not afk, always happens when a vote starts
        return afkPlayers.containsKey(player)
    }
    // TODO: Make this applied by default to the vote requirements
    // Current implementation is manually adding it to every vote file
    override fun getAfkPlayerCount(): Int = afkPlayers.size
}