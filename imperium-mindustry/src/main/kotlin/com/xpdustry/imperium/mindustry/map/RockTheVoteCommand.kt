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
package com.xpdustry.imperium.mindustry.map

import arc.Events
import cloud.commandframework.kotlin.extension.commandBuilder
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.command.SimpleVoteManager
import com.xpdustry.imperium.mindustry.command.VoteManager
import com.xpdustry.imperium.mindustry.misc.registerCopy
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Player
import kotlin.time.Duration.Companion.minutes

class RockTheVoteCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val clientCommandManager = instances.get<ImperiumPluginCommandManager>("client")

    // TODO Replace Unit with next map
    private val manager = SimpleVoteManager<Unit>(
        plugin = instances.get(),
        duration = 1.minutes,
        finished = {
            if (it.status != VoteManager.Session.Status.SUCCESS) {
                Call.sendMessage("[orange]The RTV vote failed.")
            } else {
                Call.sendMessage("[orange]The RTV vote passed. The map will be changed.")
                Events.fire(EventType.GameOverEvent(Team.derelict))
            }
        },
    )
    override fun onImperiumInit() {
        clientCommandManager.commandBuilder("rtv") {
            handler { ctx ->
                if (manager.session == null) {
                    manager.start(ctx.sender.player, true, Unit)
                    Call.sendMessage("[orange]A vote to change the map has started. Type [accent]/rtv y[] to vote.")
                } else {
                    Call.sendMessage("[orange]A RTV is already in progress.")
                }
            }

            registerCopy("yes", aliases = arrayOf("y")) {
                commandDescription("Vote yes to change the map.")
                handler { vote(it.sender.player, manager.session, true) }
            }

            registerCopy("no", aliases = arrayOf("n")) {
                commandDescription("Vote no to change the map.")
                handler { vote(it.sender.player, manager.session, false) }
            }
        }
    }

    private fun vote(player: Player, session: VoteManager.Session<Unit>?, value: Boolean) {
        if (session == null) {
            player.sendMessage("[orange]There is no RTV in progress.")
        } else if (session.getVote(player) != null) {
            player.sendMessage("[orange]You have already voted.")
        } else {
            Call.sendMessage("${player.name()} [orange] has voted to change the map.")
            session.setVote(player, value)
        }
    }
}
