// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.control

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.IMPERIUM_SCOPE
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.control.RemoteActionMessage
import com.xpdustry.imperium.common.control.toExitStatus
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.translation.server_restart_delay
import com.xpdustry.imperium.mindustry.translation.server_restart_empty
import com.xpdustry.imperium.mindustry.translation.server_restart_game_over
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.EventType.GameOverEvent
import mindustry.server.ServerControl

@Inject
class ControlListener(
    private val config: ImperiumConfig,
    private val messenger: MessageService,
    private val application: ImperiumApplication,
    @Named(IMPERIUM_SCOPE) private val scope: CoroutineScope,
) : ImperiumApplication.Listener {
    private var job: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    private var pending: PendingAction? = null

    override fun onImperiumInit() {
        messenger.subscribe<RemoteActionMessage> {
            if (it.target == null || it.target == config.server.name) {
                runMindustryThread { prepareAction(it.action, it.immediate, it.waitForEmpty) }
            }
        }
    }

    @EventHandler
    fun onPlayerJoinNotify(event: EventType.PlayerJoin) {
        if (pending?.trigger == Trigger.EMPTY) {
            event.player.asAudience.sendMessage(server_restart_empty("admin"))
        } else if (job != null || pending != null) {
            event.player.asAudience.sendMessage(server_restart_game_over("admin"))
        }
    }

    @EventHandler
    fun onGameOver(event: GameOverEvent) {
        val pending = pending?.takeIf { it.trigger == Trigger.GAME_OVER } ?: return
        this.pending = null
        scheduleAction(pending.action, 5.seconds)
    }

    @EventHandler
    fun onPlayerLeave(event: EventType.PlayerLeave) {
        val pending = pending?.takeIf { it.trigger == Trigger.EMPTY } ?: return
        // The leaving player is still in the player group when this event is fired
        if (Entities.getPlayers().any { it != event.player }) return
        this.pending = null
        scheduleAction(pending.action, 10.seconds)
    }

    // TODO Allow custom reasons
    private fun prepareAction(
        action: RemoteActionMessage.Action,
        immediate: Boolean,
        waitForEmpty: Boolean,
        reason: String = "admin",
    ) {
        job = null
        pending = null
        val everyone = Distributor.get().audienceProvider.everyone
        when {
            immediate ||
                Entities.getPlayers().isEmpty() ||
                Vars.state.gameOver ||
                config.mindustry.gamemode == MindustryGamemode.HUB -> {
                everyone.sendMessage(server_restart_delay(reason, 10.seconds))
                scheduleAction(action, 10.seconds)
            }
            waitForEmpty -> {
                everyone.sendMessage(server_restart_empty(reason))
                pending = PendingAction(action, Trigger.EMPTY)
                scheduleAction(action, WAIT_FOR_EMPTY_TIMEOUT)
            }
            config.mindustry.gamemode.pvp -> {
                everyone.sendMessage(server_restart_game_over(reason))
                pending = PendingAction(action, Trigger.GAME_OVER)
            }
            else -> {
                everyone.sendMessage(server_restart_delay(reason, 5.minutes))
                scheduleAction(action, 5.minutes)
            }
        }
    }

    private fun scheduleAction(action: RemoteActionMessage.Action, delay: Duration) {
        job = scope.launch {
            delay(delay)
            doAction(action)
        }
    }

    private fun doAction(action: RemoteActionMessage.Action) {
        when (action) {
            RemoteActionMessage.Action.CLOSE ->
                ServerControl.instance.handleCommandString("stop") // TODO Find a cleaner way
            else -> application.exit(action.toExitStatus())
        }
    }

    private data class PendingAction(val action: RemoteActionMessage.Action, val trigger: Trigger)

    private enum class Trigger {
        GAME_OVER,
        EMPTY,
    }

    companion object {
        private val WAIT_FOR_EMPTY_TIMEOUT = 1.hours
    }
}
