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
import com.xpdustry.imperium.common.command.Permission
import com.xpdustry.imperium.common.command.annotation.Min
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.SimpleVoteManager
import com.xpdustry.imperium.mindustry.command.VoteManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.menu.MenuInterface
import com.xpdustry.imperium.mindustry.ui.menu.MenuOption
import fr.xpdustry.distributor.api.command.sender.CommandSender
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import mindustry.Vars
import mindustry.gen.Call

class WaveCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val voteNewWave =
        SimpleVoteManager<Int>(
            plugin = instances.get(),
            duration = 45.seconds,
            finished = {
                if (it.status != VoteManager.Session.Status.SUCCESS) {
                    Call.sendMessage("[scarlet]Vote to skip the wave failed.")
                    return@SimpleVoteManager
                }
                runMindustryThread {
                    Vars.state.wave += it.target
                    Vars.state.wavetime = Vars.state.rules.waveSpacing
                    Call.sendMessage("[green]Skipped ${it.target} wave(s).")
                }
            })

    private val waveSkipInterface =
        MenuInterface.create(instances.get()).apply {
            addTransformer { _, pane ->
                pane.title = "Skip Wave"
                pane.content = "How many waves do you want to skip?"
                pane.options.addRow(
                    listOf(3, 5, 10, 15).map { skip ->
                        MenuOption("[orange]$skip") { view ->
                            view.close()
                            if (voteNewWave.session != null)
                                return@MenuOption view.viewer.sendMessage(
                                    "A vote is already running.")
                            val session = voteNewWave.start(view.viewer, true, skip)
                            Call.sendMessage(
                                "[green]${view.viewer.name} started a vote to skip the $skip wave(s). ${session.required} are required.")
                        }
                    })
                pane.options.addRow(MenuOption("[lightgray]Cancel", View::closeAll))
            }
        }

    @Command(["wave", "set", "time"], Permission.MODERATOR)
    @ClientSide
    private fun onWaveSetTime(sender: CommandSender, time: Duration) {
        Vars.state.wavetime = time.seconds.toFloat() * 60F
        sender.sendMessage("Set wave time to $time")
    }

    @Command(["wave", "set", "counter"], Permission.MODERATOR)
    @ClientSide
    private fun onWaveSetCounter(sender: CommandSender, wave: Int) {
        Vars.state.wave = wave
        Vars.state.wavetime = Vars.state.rules.waveSpacing
        sender.sendMessage("Set wave to counter $wave")
    }

    @Command(["wave", "run"], Permission.MODERATOR)
    @ClientSide
    private fun onWaveRun(sender: CommandSender, @Min(0) waves: Int = 0) {
        repeat(waves) { Vars.logic.runWave() }
        sender.sendMessage("Skipped $waves wave(s).")
    }

    @Command(["wave", "skip"])
    @ClientSide
    private fun onWaveSkip(sender: CommandSender) {
        waveSkipInterface.open(sender.player)
    }

    @Command(["ws", "y"])
    @ClientSide
    private fun onWaveSkipYes(sender: CommandSender) {
        val session = voteNewWave.session ?: return sender.sendMessage("No vote is running.")
        if (session.getVote(sender.player) != null) return sender.sendMessage("You already voted.")
        Call.sendMessage(
            "[green]${sender.player.name} voted to skip the wave. (${session.votes + 1}/${session.required})")
        session.setVote(sender.player, true)
    }

    @Command(["ws", "n"])
    @ClientSide
    private fun onWaveSkipNo(sender: CommandSender) {
        val session = voteNewWave.session ?: return sender.sendMessage("No vote is running.")
        if (session.getVote(sender.player) != null) return sender.sendMessage("You already voted.")
        Call.sendMessage(
            "[green]${sender.player.name} voted to not skip the wave. (${session.votes - 1}/${session.required})")
        session.setVote(sender.player, false)
    }
}
