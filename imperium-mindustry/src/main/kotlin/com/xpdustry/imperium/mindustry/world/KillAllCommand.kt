/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.ImperiumPermission
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlin.time.Duration.Companion.seconds
import mindustry.game.Team
import mindustry.gen.Call

class KillAllCommand(instances: InstanceManager) :
    AbstractVoteCommand<Unit>(instances.get(), "killall", 30.seconds),
    ImperiumApplication.Listener {

    @ImperiumCommand(["killall|ku"])
    @ClientSide
    @ImperiumPermission(gamemodes = [MindustryGamemode.SANDBOX])
    fun onKillUnitsCommand(sender: CommandSender) {
        onVoteSessionStart(sender.player, manager.session, Unit)
    }

    @ImperiumCommand(["killall|ku", "y"])
    @ClientSide
    @ImperiumPermission(gamemodes = [MindustryGamemode.SANDBOX])
    fun onKillUnitsYesCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.YES)
    }

    @ImperiumCommand(["killall|ku", "n"])
    @ClientSide
    @ImperiumPermission(gamemodes = [(MindustryGamemode.SANDBOX)])
    fun onKillUnitsNoCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.NO)
    }

    @ImperiumCommand(["killall|ku", "c"])
    @ClientSide
    @ImperiumPermission(Rank.MODERATOR, MindustryGamemode.SANDBOX)
    fun onKillUnitsCancelCommand(sender: CommandSender) {
        onPlayerCancel(sender.player, manager.session)
    }

    @ImperiumCommand(["killall|ku", "team|t"])
    @ImperiumPermission(Rank.MODERATOR)
    @ClientSide
    fun onKillUnitsTeamCommand(sender: CommandSender, team: Team) {
        var count = 0
        for (unit in Entities.getUnits().toList()) {
            if (!unit.isPlayer && team == unit.team()) {
                count++
                Call.unitDespawn(unit)
            }
        }
        sender.sendMessage("Killed $count unit(s) from team ${team.coloredName()}")
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<Unit>): String =
        "Type [orange]/killall <y/n>[] to kill all units."

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<Unit>) {
        runMindustryThread {
            Entities.getUnits().toList().forEach { unit ->
                if (!unit.isPlayer) Call.unitDespawn(unit)
            }
        }
    }
}
