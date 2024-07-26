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

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.onEvent
import kotlinx.coroutines.delay
import mindustry.Vars
import mindustry.core.GameState
import mindustry.game.EventType.PlayerJoin
import mindustry.gen.Call

class UnpauseCommand : ImperiumApplication.Listener {

    init {
        onEvent<PlayerJoin> {
            if (Vars.state.isPaused()) {
                Call.sendMessage(
                    "[lightgray]The server is paused, type [orange]/unpause[lightgray] to unpause the server")
            }
        }
    }

    @ImperiumCommand(["unpause"])
    @ClientSide
    suspend fun onUnpauseCommand(sender: CommandSender) {
        if (Vars.state.isPaused()) {
            Vars.state.set(GameState.State.playing)
            delay(1500)
            Call.sendMessage(
                "${sender.player.name}[white] unpaused the server using [orange]/unpause")
        } else {
            sender.player.sendMessage("The server is already unpaused")
        }
    }
}
