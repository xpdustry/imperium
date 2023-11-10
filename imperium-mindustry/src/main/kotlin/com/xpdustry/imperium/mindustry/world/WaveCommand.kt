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

import com.xpdustry.imperium.common.account.Role
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.annotation.Max
import com.xpdustry.imperium.common.command.annotation.Min
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import fr.xpdustry.distributor.api.command.sender.CommandSender
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import mindustry.Vars
import mindustry.gen.Call

class WaveCommand(instances: InstanceManager) :
    AbstractVoteCommand<Int>(instances.get(), "wave-skip", 45.seconds),
    ImperiumApplication.Listener {
    private val waveSkipInterface =
        MenuInterface.create(instances.get()).apply {
            addTransformer { _, pane ->
                pane.title = "Skip Wave"
                pane.content = "How many waves do you want to skip?"
                pane.options.addRow(
                    listOf(3, 5, 10, 15).map { skip ->
                        MenuOption("[orange]$skip") { view ->
                            view.closeAll()
                            onVoteSessionStart(view.viewer, manager.session, skip)
                        }
                    })
                pane.options.addRow(MenuOption("[lightgray]Cancel", View::closeAll))
            }
        }

    @Command(["wave", "set", "countdown"], Role.MODERATOR)
    @ClientSide
    private fun onWaveSetTime(sender: CommandSender, duration: Duration) {
        Vars.state.wavetime = duration.seconds.toFloat() * 60F
        sender.sendMessage("Set wave countdown to $duration")
    }

    @Command(["wave", "set", "counter"], Role.MODERATOR)
    @ClientSide
    private fun onWaveSetCounter(sender: CommandSender, wave: Int) {
        Vars.state.wave = wave
        Vars.state.wavetime = Vars.state.rules.waveSpacing
        sender.sendMessage("Set wave to counter $wave")
    }

    @Command(["wave", "run"], Role.MODERATOR)
    @ClientSide
    private fun onWaveRun(sender: CommandSender, @Min(1) @Max(20) count: Int = 1) {
        repeat(count) { Vars.logic.runWave() }
        sender.sendMessage("Ran $count wave(s).")
    }

    @Command(["wave", "skip"])
    @ClientSide
    private fun onWaveSkip(sender: CommandSender) {
        waveSkipInterface.open(sender.player)
    }

    @Command(["ws", "y"])
    @ClientSide
    private fun onWaveSkipYes(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.YES)
    }

    @Command(["ws", "n"])
    @ClientSide
    private fun onWaveSkipNo(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.NO)
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<Int>): String =
        "Type [accent]/ws y[] to vote to skip ${session.objective} wave(s)."

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<Int>) {
        runMindustryThread {
            Vars.state.wave += session.objective
            Vars.state.wavetime = Vars.state.rules.waveSpacing
            Call.sendMessage("[green]Skipped ${session.objective} wave(s).")
        }
    }
}
