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
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import mindustry.Vars
import mindustry.core.GameState

class PauseListener : ImperiumApplication.Listener {

    @ImperiumCommand(["pause"], rank = Rank.MODERATOR)
    @ClientSide
    fun onPauseCommand(sender: CommandSender) {
        when (Vars.state.state!!) {
            GameState.State.paused -> {
                Vars.state.set(GameState.State.playing)
                sender.reply("The server has been un-paused")
            }
            GameState.State.playing -> {
                Vars.state.set(GameState.State.paused)
                sender.reply("The server has been paused")
            }
            GameState.State.menu -> {
                sender.error("The server is not running")
            }
        }
    }
}
