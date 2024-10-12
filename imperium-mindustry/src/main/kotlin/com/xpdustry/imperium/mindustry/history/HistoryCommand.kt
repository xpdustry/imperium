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
package com.xpdustry.imperium.mindustry.history

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.MindustryConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Player
import org.incendo.cloud.annotation.specifier.Range

class HistoryCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val historian = instances.get<Historian>()
    private val taps = PlayerMap<Long>(instances.get())
    private val users = instances.get<UserManager>()
    private val config = instances.get<MindustryConfig>()
    private val historyRenderer = instances.get<HistoryRenderer>()

    @EventHandler
    internal fun onPlayerTapEvent(event: EventType.TapEvent) =
        ImperiumScope.MAIN.launch {
            if (users.getSetting(event.player.uuid(), User.Setting.DOUBLE_TAP_TILE_LOG)) {
                val last = taps[event.player]
                if (last != null &&
                    (System.currentTimeMillis() - last).milliseconds <
                        config.history.doubleClickDelay) {
                    taps.remove(event.player)
                    onTileHistoryCommand(
                        CommandSender.player(event.player), event.tile.x, event.tile.y)
                } else {
                    taps[event.player] = System.currentTimeMillis()
                }
            }
        }

    @ImperiumCommand(["history", "player"])
    @ClientSide
    @ServerSide
    suspend fun onPlayerHistoryCommand(
        sender: CommandSender,
        player: Player,
        @Range(min = "1", max = "50") limit: Int = 10
    ) {
        val entries = runMindustryThread { historian.getHistory(player.uuid()).normalize(limit) }
        if (entries.none()) {
            sender.error(translatable("imperium.history.none"))
        } else {
            sender.reply(historyRenderer.render(entries, HistoryActor(player)))
        }
    }

    @ImperiumCommand(["history", "tile"])
    @ClientSide
    @ServerSide
    suspend fun onTileHistoryCommand(
        sender: CommandSender,
        @Range(min = "1") x: Short,
        @Range(min = "1") y: Short,
        @Range(min = "1", max = "50") limit: Int = 10,
    ) {
        val entries = runMindustryThread {
            historian.getHistory(x.toInt(), y.toInt()).normalize(limit)
        }
        if (entries.none()) {
            sender.error(translatable("imperium.history.none"))
        } else {
            sender.reply(historyRenderer.render(entries, x.toInt(), y.toInt()))
        }
    }
}
