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
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.SimpleVoteManager
import com.xpdustry.imperium.mindustry.command.VoteManager
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import fr.xpdustry.distributor.api.command.sender.CommandSender
import kotlin.time.Duration.Companion.seconds
import mindustry.gen.Call
import mindustry.gen.Groups

class KillCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val voteKillAll =
        SimpleVoteManager<Unit>(
            plugin = instances.get(),
            duration = 20.seconds,
            finished = { session ->
                if (session.status != VoteManager.Session.Status.SUCCESS) {
                    Call.sendMessage("[scarlet]Vote to kill all units failed.")
                    return@SimpleVoteManager
                }
                runMindustryThread {
                    Groups.unit.toList().forEach { unit ->
                        if (!unit.isPlayer) Call.unitDespawn(unit)
                    }
                }
            })

    /*
    @Command(["kill"], permission = Permission.MODERATOR)
    private suspend fun onKillCommand(sender: CommandSender) {
        if (sender.player.unit() == Nulls.unit || sender.player.unit().dead) {
            sender.sendMessage("Mate, you're already dead.")
        } else {
            runMindustryThread {
                sender.sendMessage("[scarlet]KYS.")
                sender.player.unit().kill()
            }
        }
    }
     */

    @Command(["ku"])
    private fun onKillUnitsCommand(sender: CommandSender) {
        if (voteKillAll.session != null) {
            sender.sendMessage("There is already a vote to kill all units.")
            return
        }
        voteKillAll.start(sender.player, true, Unit)
    }

    @Command(["ku", "y"])
    private fun onKillUnitsYesCommand(sender: CommandSender) {
        val session =
            voteKillAll.session ?: return sender.sendMessage("There is no vote to kill all units.")
        if (session.getVote(sender.player) != null)
            return sender.sendMessage("You have already voted.")
        session.setVote(sender.player, true)
        Call.sendMessage("[green]${sender.player.name} voted yes to kill all units.")
    }

    @Command(["ku", "n"])
    private fun onKillUnitsNoCommand(sender: CommandSender) {
        val session =
            voteKillAll.session ?: return sender.sendMessage("There is no vote to kill all units.")
        if (session.getVote(sender.player) != null)
            return sender.sendMessage("You have already voted.")
        session.setVote(sender.player, false)
        Call.sendMessage("[green]${sender.player.name} voted no to kill all units.")
    }
}
