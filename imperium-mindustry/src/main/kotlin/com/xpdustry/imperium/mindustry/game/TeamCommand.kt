// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.translation.command_team_success
import mindustry.game.Team

class TeamCommand : ImperiumApplication.Listener {

    // TODO: Make this use a menu for the base game teams
    // Custom ID option?
    @ImperiumCommand(["team"], Rank.VERIFIED)
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onTeamCommand(sender: CommandSender, team: Team) {
        sender.player.team(team)
        sender.player.asAudience.sendMessage(command_team_success(team))
    }
}
