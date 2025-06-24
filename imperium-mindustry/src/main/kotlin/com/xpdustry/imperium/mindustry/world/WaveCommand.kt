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
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import mindustry.Vars
import mindustry.gen.Call
import org.incendo.cloud.annotation.specifier.Range

class WaveCommand(instances: InstanceManager) :
    AbstractVoteCommand<Int>(instances.get(), "wave-skip", 45.seconds), ImperiumApplication.Listener {
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
                    }
                )
                pane.options.addRow(MenuOption("[lightgray]Cancel", View::closeAll))
            }
        }

    @ImperiumCommand(["wave", "set", "countdown"], Rank.MODERATOR)
    @ClientSide
    fun onWaveSetTime(sender: CommandSender, duration: Duration) {
        Vars.state.wavetime = duration.seconds.toFloat() * 60F
        sender.reply("Set wave countdown to $duration")
    }

    @ImperiumCommand(["wave", "set", "counter"], Rank.MODERATOR)
    @ClientSide
    fun onWaveSetCounter(sender: CommandSender, wave: Int) {
        Vars.state.wave = wave
        Vars.state.wavetime = Vars.state.rules.waveSpacing
        sender.reply("Set wave to counter $wave")
    }

    @ImperiumCommand(["wave", "run"], Rank.MODERATOR)
    @ClientSide
    fun onWaveRun(sender: CommandSender, @Range(min = "1", max = "20") count: Int = 1) {
        repeat(count) { Vars.logic.runWave() }
        sender.reply("Ran $count wave(s).")
    }

    @ImperiumCommand(["wave", "skip"], Rank.MODERATOR)
    @ClientSide
    fun onWaveSkip(sender: CommandSender) {
        waveSkipInterface.open(sender.player)
    }

    @ImperiumCommand(["ws", "y"])
    @ClientSide
    fun onWaveSkipYes(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.YES)
    }

    @ImperiumCommand(["ws", "n"])
    @ClientSide
    fun onWaveSkipNo(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.NO)
    }

    @ImperiumCommand(["ws", "cancel|c"], Rank.MODERATOR)
    @ClientSide
    fun onWaveSkipCancelCommand(sender: CommandSender) {
        onPlayerCancel(sender.player, manager.session)
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<Int>): String =
        "Type [accent]/ws y[] to vote to skip [accent]${session.objective}[] wave(s)."

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<Int>) = runMindustryThread {
        Vars.state.wave += session.objective
        Vars.state.wavetime = Vars.state.rules.waveSpacing
        Call.sendMessage("[green]Skipped ${session.objective} wave(s).")
    }
}
