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
package com.xpdustry.imperium.discord.command.standard

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.MindustryPlayerMessage
import com.xpdustry.imperium.common.bridge.PlayerInfo
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.misc.LimitedList
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.discord.command.Command
import com.xpdustry.imperium.discord.command.CommandActor
import kotlinx.coroutines.future.await
import org.javacord.api.entity.message.embed.EmbedBuilder
import java.time.Instant
import java.util.Queue

@Command("server")
class ServerCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discovery = instances.get<Discovery>()
    private val messenger = instances.get<Messenger>()
    private val history = mutableMapOf<String, Queue<PlayerJoinEntry>>()

    override fun onImperiumInit() {
        // TODO Implement player join history
        messenger.subscribe<MindustryPlayerMessage.Join> {
            history.computeIfAbsent(it.serverName) { LimitedList(20) }.add(PlayerJoinEntry(it.player))
        }
    }

    @Command("list")
    suspend fun onServerList(actor: CommandActor) =
        actor.updater.addEmbed(
            EmbedBuilder()
                .setTitle("Server List")
                .setDescription(discovery.servers.joinToString(separator = "\n") { " - " + it.serverName }),
        ).update().await()

    data class PlayerJoinEntry(val player: PlayerInfo, val timestamp: Instant = Instant.now())
}
