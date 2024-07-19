import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.onEvent
import mindustry.game.EventType.PlayerJoin
import mindustry.game.EventType.Trigger

package com.xpdustry.imperium.mindustry.game

class UnpauseCommand : ImperiumApplication.Listener {

  fun joinListener() {
    onEvent(Trigger.PlayerJoin
    onEvent<PlayeJoin>
    
  }
  
  @ImperiumCommand(["unpause"])
  @ClientSide
  fun onUnpauseCommand(sender: CommandSender) {

    if (Vars.state.paused) {
      Vars.state.paused = false
    }

    Call.sendChatMessage("${sender.player.name}[white] unpaused the server using [orange]/unpause")
  }
}
