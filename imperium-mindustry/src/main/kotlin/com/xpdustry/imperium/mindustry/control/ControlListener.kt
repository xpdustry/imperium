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
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.control.RemoteActionMessage
import com.xpdustry.imperium.common.control.toExitStatus
import com.xpdustry.imperium.common.lifecycle.ExitService
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.onEvent
import com.xpdustry.imperium.mindustry.translation.server_restart_delay
import com.xpdustry.imperium.mindustry.translation.server_restart_game_over
import jakarta.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType.GameOverEvent

class ControlListener
@Inject
constructor(private val config: ImperiumConfig, private val messenger: Messenger, private val exit: ExitService) :
    LifecycleListener {

    private var job: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    override fun onImperiumInit() {
        messenger.consumer<RemoteActionMessage> {
            if (it.target == null || it.target == config.server.name) {
                prepareAction("admin", it.action, it.immediate)
            }
        }
    }

    private fun prepareAction(reason: String, action: RemoteActionMessage.Action, immediate: Boolean) {
        job = null
        val everyone = Distributor.get().audienceProvider.everyone
        if (immediate || Entities.getPlayers().isEmpty() || Vars.state.gameOver) {
            everyone.sendMessage(server_restart_delay(reason, 10.seconds))
            job =
                ImperiumScope.MAIN.launch {
                    delay(10.seconds)
                    exit.exit(action.toExitStatus())
                }
        } else if (config.mindustry.gamemode.pvp) {
            everyone.sendMessage(server_restart_game_over(reason))
            onEvent<GameOverEvent> {
                job =
                    ImperiumScope.MAIN.launch {
                        delay(5.seconds)
                        exit.exit(action.toExitStatus())
                    }
            }
        } else if (config.mindustry.gamemode == MindustryGamemode.HUB) {
            everyone.sendMessage(server_restart_delay(reason, 10.seconds))
            job =
                ImperiumScope.MAIN.launch {
                    delay(10.seconds)
                    exit.exit(action.toExitStatus())
                }
        } else {
            everyone.sendMessage(server_restart_delay(reason, 5.minutes))
            job =
                ImperiumScope.MAIN.launch {
                    delay(5.minutes)
                    exit.exit(action.toExitStatus())
                }
        }
    }
}
