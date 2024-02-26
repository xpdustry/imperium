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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import fr.xpdustry.distributor.api.command.sender.CommandSender
import kotlin.time.Duration.Companion.seconds
import mindustry.gen.Call

class KillAllCommand(instances: InstanceManager) :
    AbstractVoteCommand<Unit>(instances.get(), "killall", 30.seconds),
    ImperiumApplication.Listener {

    @Command(["killall|ku"])
    @ClientSide
    @Scope(MindustryGamemode.SANDBOX)
    private fun onKillUnitsCommand(sender: CommandSender) {
        onVoteSessionStart(sender.player, manager.session, Unit)
    }

    @Command(["killall|ku", "y"])
    @ClientSide
    @Scope(MindustryGamemode.SANDBOX)
    private fun onKillUnitsYesCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.YES)
    }

    @Command(["killall|ku", "n"])
    @ClientSide
    @Scope(MindustryGamemode.SANDBOX)
    private fun onKillUnitsNoCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.NO)
    }

    @Command(["killall|ku", "c"]. Rank.MODERATOR)
    @ClientSide
    @Scope(MindustryGamemode.SANDBOX)
    private fun onKillUnitsCancelCommand(sender: CommandSender) {
        onPlayerCancel(sender.player, manager.session)
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
