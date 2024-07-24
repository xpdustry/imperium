package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.onEvent
import mindustry.core.GameState
import mindustry.game.EventType.PlayerJoin
import mindustry.gen.Call
import mindustry.Vars

class UnpauseCommand : ImperiumApplication.Listener {

  onEvent<PlayerJoin> {
    if (Vars.state.isPaused())
      Call.sendChatMessage("[lightgray]The server is paused, type [orange]/unpause[lightgray] to unpause the server")
  }

  @ImperiumCommand(["unpause"])
  @ClientSide
  fun onUnpauseCommand(sender: CommandSender) {
    if (Vars.state.isPaused()) {
      Vars.state.set(GameState.State.playing)
      Call.sendChatMessage("${sender.player.name}[white] unpaused the server using [orange]/unpause")
    } else {
      Call.sendMessage("The server is already unpaused")
    }
  }
}
