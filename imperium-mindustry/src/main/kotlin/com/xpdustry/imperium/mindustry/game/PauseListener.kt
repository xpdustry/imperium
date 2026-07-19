// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import mindustry.Vars
import mindustry.core.GameState

@Inject
class PauseListener constructor() : ImperiumApplication.Listener {

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
