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
import com.xpdustry.imperium.mindustry.misc.onEvent
import com.xpdustry.imperium.mindustry.translation.server_restart_delay
import com.xpdustry.imperium.mindustry.translation.server_restart_game_over
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

    override fun onImperiumInit() {
        messenger.subscribe<RemoteActionMessage> {
            if (it.target == null || it.target == config.server.name) {
                prepareAction(it.action, it.immediate)
            }
        }
    }

    @EventHandler
    fun onPlayerJoinNotify(event: EventType.PlayerJoin) {
        if (job != null) {
            event.player.asAudience.sendMessage(server_restart_game_over("admin"))
        }
    }

    // TODO Allow custom reasons
    private fun prepareAction(action: RemoteActionMessage.Action, immediate: Boolean, reason: String = "admin") {
        job = null
        val everyone = Distributor.get().audienceProvider.everyone
        if (immediate || Entities.getPlayers().isEmpty() || Vars.state.gameOver) {
            everyone.sendMessage(server_restart_delay(reason, 10.seconds))
            job =
                scope.launch {
                    delay(10.seconds)
                    doAction(action)
                }
        } else if (config.mindustry.gamemode.pvp) {
            everyone.sendMessage(server_restart_game_over(reason))
            onEvent<GameOverEvent> {
                job =
                    scope.launch {
                        delay(5.seconds)
                        doAction(action)
                    }
            }
        } else if (config.mindustry.gamemode == MindustryGamemode.HUB) {
            everyone.sendMessage(server_restart_delay(reason, 10.seconds))
            job =
                scope.launch {
                    delay(10.seconds)
                    doAction(action)
                }
        } else {
            everyone.sendMessage(server_restart_delay(reason, 5.minutes))
            job =
                scope.launch {
                    delay(5.minutes)
                    doAction(action)
                }
        }
    }

    private fun doAction(action: RemoteActionMessage.Action) {
        when (action) {
            RemoteActionMessage.Action.CLOSE ->
                ServerControl.instance.handleCommandString("stop") // TODO Find a cleaner way
            else -> application.exit(action.toExitStatus())
        }
    }
}
