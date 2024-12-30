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

import com.xpdustry.distributor.api.annotation.TriggerHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import mindustry.Vars
import mindustry.core.GameState
import mindustry.game.EventType
import mindustry.gen.Groups
import mindustry.net.Administration

class PauseListener : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        // autoPause is bad
        Administration.Config.autoPause.set(false)
    }

    // TODO: remove when v8 is released
    @TriggerHandler(EventType.Trigger.update)
    fun pauseListener() {
        if (Vars.state.isPlaying && Groups.player.size() == 0) {
            Vars.state.set(GameState.State.paused)
        } else if (Vars.state.isPaused && Groups.player.size() > 0) {
            Vars.state.set(GameState.State.playing)
        }
    }

    @ImperiumCommand(["unpause"])
    @ClientSide
    @ServerSide
    fun onUnpauseCommand(sender: CommandSender) {
        when (Vars.state.state!!) {
            GameState.State.playing -> sender.error("The server is already unpaused")
            GameState.State.paused -> Vars.state.set(GameState.State.playing)
            GameState.State.menu -> sender.error("The server is not running")
        }
    }
}
