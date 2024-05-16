package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide

class AdminToggle : ImperiumApplication.Listener {

    @ImperiumCommand(["admin"], Rank.OVERSEER)
    @ClientSide
    fun onAdminToggleCommand(
        sender: CommandSender
        ) {
        sender.player.admin = !sender.player.admin
        sender.player.sendMessage("[accent]Your admin status has been set to ${sender.player.admin}")
    } 
}

// I plan on moving this to a centralised file,
// with other moderation based mindustry commands in the future #142