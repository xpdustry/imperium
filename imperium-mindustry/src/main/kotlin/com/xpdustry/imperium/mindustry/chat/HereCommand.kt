// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import mindustry.gen.Call

class HereCommand : ImperiumApplication.Listener {

    @ImperiumCommand(["here"])
    @ClientSide
    fun onHereCommand(sender: CommandSender) {
        Call.sendMessage(
            "${sender.player.coloredName()}[white] is at (${sender.player.tileX()}, ${sender.player.tileY()})."
        )
    }
}
