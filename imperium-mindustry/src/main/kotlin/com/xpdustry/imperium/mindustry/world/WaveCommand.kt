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
import com.xpdustry.distributor.api.component.NumberComponent.number
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.security.AfkManager
import jakarta.inject.Inject
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import mindustry.Vars
import mindustry.gen.Call
import org.incendo.cloud.annotation.specifier.Range

class WaveCommand @Inject constructor(plugin: MindustryPlugin, afk: AfkManager) :
    AbstractVoteCommand<Int>(plugin, "wave-skip", afk, 45.seconds), LifecycleListener {
    private val waveSkipInterface =
        MenuManager.create(plugin).apply {
            addTransformer { (pane) ->
                pane.title = text("Skip Wave")
                pane.content = text("How many waves do you want to skip?")
                pane.grid.addRow(
                    listOf(3, 5, 10, 15).map { skip ->
                        MenuOption.of(
                            number(skip, ComponentColor.ORANGE),
                            Action.hideAll().then { window -> onVoteSessionStart(window.viewer, manager.session, skip) },
                        )
                    }
                )
                pane.grid.addRow(MenuOption.of(text("Cancel", ComponentColor.LIGHT_GRAY), Action.hideAll()))
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
        waveSkipInterface.create(sender.player).show()
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

    @ImperiumCommand(["ws", "cancel|c"], Rank.OVERSEER)
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
