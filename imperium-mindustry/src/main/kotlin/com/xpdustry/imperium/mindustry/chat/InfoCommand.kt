package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide

class InfoCommand : ImperiumApplication.Listener {

    // Dont require a page for 1 page
    @ImperiumCommand(["info|i"])
    @ClientSide
    fun onInfoCommandSingle(sender: CommandSender) {
        onInfoCommand(sender, 1)
    }

    @ImperiumCommand(["info|i"])
    @ClientSide
    fun onInfoCommand(sender: CommandSender, page: Int = 1) {
        sender.player.sendMessage("")
    }

    enum class
}