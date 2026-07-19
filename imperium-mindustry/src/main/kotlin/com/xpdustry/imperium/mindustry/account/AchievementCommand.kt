// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.translation.command_achievements

@Inject
class AchievementCommand constructor() : ImperiumApplication.Listener {

    @ImperiumCommand(["achievements"])
    @ClientSide
    @ServerSide
    fun onAchievementsCommand(sender: CommandSender, achievement: Achievement? = null) {
        sender.reply(command_achievements(achievement?.let(::listOf) ?: Achievement.entries))
    }
}
