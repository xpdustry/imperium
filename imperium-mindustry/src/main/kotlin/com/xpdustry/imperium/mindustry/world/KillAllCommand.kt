/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
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

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Flag
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.formation.FormationAI
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlin.time.Duration.Companion.seconds
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.type.UnitType

class KillAllCommand(instances: InstanceManager) :
    AbstractVoteCommand<Unit>(instances.get(), "killall", 30.seconds), ImperiumApplication.Listener {

    @ImperiumCommand(["killall|ku"])
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onKillAllUnitsCommand(sender: CommandSender) {
        onVoteSessionStart(sender.player, manager.session, Unit)
    }

    @ImperiumCommand(["killall|ku", "y"])
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onKillAllUnitsYesCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.YES)
    }

    @ImperiumCommand(["killall|ku", "n"])
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onKillAllUnitsNoCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.NO)
    }

    @ImperiumCommand(["killall|ku", "c"], Rank.MODERATOR)
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onKillAllUnitsCancelCommand(sender: CommandSender) {
        onPlayerCancel(sender.player, manager.session)
    }

    @ImperiumCommand(["killall|ku", "team|t"], Rank.MODERATOR)
    @ClientSide
    fun onKillUnitsTeamCommand(sender: CommandSender, team: Team, @Flag("u") unittype: UnitType? = null) {
        var count = 0
        for (unit in Entities.getUnits().toList()) {
            if (!unit.isPlayer && unit.controller() !is FormationAI && team == unit.team()) {
                if (unittype == null) {
                    Call.unitDespawn(unit)
                    count++
                } else if (unit.type == unittype) {
                    Call.unitDespawn(unit)
                    count++
                }
            }
        }
        // TODO: make this translatable
        sender.reply("Killed $count ${unittype?.localizedName ?: "unit"}(s) from team ${team.coloredName()}")
    }

    // Kill individual units
    // TODO: merge with the above command? Make team optional?
    @ImperiumCommand(["kill|k", "unittype|u"], Rank.MODERATOR)
    @ClientSide
    fun onKillUnitsCommand(
        sender: CommandSender,
        unittype: UnitType,
        @Flag("c") count: Int? = null,
        @Flag("t") team: Team? = null,
    ) {
        var amount = 0
        for (unit in Entities.getUnits().toList()) {
            if (amount == count) break
            if (!unit.isPlayer && unit.controller() !is FormationAI && (team == null || team == unit.team())) {
                if (unit.type == unittype) {
                    Call.unitDespawn(unit)
                    amount++
                }
            }
        }
        // TODO: translate this
        sender.reply(
            "Killed $amount ${unittype.localizedName}(s) ${if (team != null) "from team ${team.coloredName()}" else ""}"
        )
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<Unit>): String =
        // TODO: this to
        "Type [orange]/killall <y/n>[] to kill all units."

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<Unit>) {
        runMindustryThread {
            Entities.getUnits().toList().forEach { unit ->
                if (!unit.isPlayer && unit.controller() !is FormationAI) Call.unitDespawn(unit)
            }
        }
    }
}
