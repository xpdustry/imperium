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
package com.xpdustry.imperium.mindustry.control

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.control.RemoteActionMessage
import com.xpdustry.imperium.common.control.toExitStatus
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.onEvent
import com.xpdustry.imperium.mindustry.translation.server_restart_delay
import com.xpdustry.imperium.mindustry.translation.server_restart_game_over
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.EventType.GameOverEvent
import mindustry.server.ServerControl

class ControlListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val config = instances.get<ImperiumConfig>()
    private val messenger = instances.get<MessageService>()
    private val application = instances.get<ImperiumApplication>()
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
                ImperiumScope.MAIN.launch {
                    delay(10.seconds)
                    doAction(action)
                }
        } else if (config.mindustry.gamemode.pvp) {
            everyone.sendMessage(server_restart_game_over(reason))
            onEvent<GameOverEvent> {
                job =
                    ImperiumScope.MAIN.launch {
                        delay(5.seconds)
                        doAction(action)
                    }
            }
        } else if (config.mindustry.gamemode == MindustryGamemode.HUB) {
            everyone.sendMessage(server_restart_delay(reason, 10.seconds))
            job =
                ImperiumScope.MAIN.launch {
                    delay(10.seconds)
                    doAction(action)
                }
        } else {
            everyone.sendMessage(server_restart_delay(reason, 5.minutes))
            job =
                ImperiumScope.MAIN.launch {
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
