package com.xpdustry.imperium.mindustry.world


import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import mindustry.gen.Call

class HereCommand : ImperiumApplication.Listener {

    @ImperiumCommand(["here"])
    @ClientSide
    fun onHereCommand(
        sender: CommandSender
    ) {
        val x = (sender.player.x / 8).toInt()
        val y = (sender.player.y / 8).toInt()
        Call.sendMessage("[[${sender.name}[white]]: I am at ($x, $y).")
    }
}

// Optionally change this file to pure chat commands file