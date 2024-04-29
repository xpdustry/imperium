package com.xpdustry.imperium.mindustry.world

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import mindustry.gen.Call

class HereCommand : ImperiumApplication {

    @ImperiumCommand(["here"])
    @ClientSide
    fun onHereCommand() (
        sender: CommandSender
    ) {
        Call.sendMessage("$sender: I am at (${sender.player.x}, ${sender.player.y}).")
    }
}