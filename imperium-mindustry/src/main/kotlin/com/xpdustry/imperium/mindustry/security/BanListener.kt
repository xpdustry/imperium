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
package com.xpdustry.imperium.mindustry.security

import arc.Events
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.BanManager
import com.xpdustry.imperium.common.security.BanMessage
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import mindustry.game.EventType.PlayerBanEvent
import mindustry.game.EventType.PlayerIpBanEvent
import mindustry.gen.Call
import mindustry.gen.Groups

class BanListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val messenger = instances.get<Messenger>()
    private val bans = instances.get<BanManager>()

    override fun onImperiumInit() {
        messenger.subscribe<BanMessage> { message ->
            val ban = bans.findById(message.id) ?: return@subscribe
            runMindustryThread {
                Events.fire(PlayerIpBanEvent(ban.target.hostAddress))
                for (player in Groups.player) {
                    if (player.ip().toInetAddress() != ban.target) {
                        continue
                    }
                    Events.fire(PlayerBanEvent(player, player.uuid()))
                    player.kick(
                        """
                        [scarlet]You have been banned for '${ban.reason}'.
                        [white]You can appeal your ban in our discord server at [cyan]https://discord.xpdustry.com[].
                        [accent]Your punishment id is [white]${ban._id}[].
                        """.trimIndent(),
                        0,
                    )
                    logger.info(
                        "{} ({}) has been banned for '{}' (details={})",
                        player.plainName(),
                        player.uuid(),
                        ban.reason,
                        ban.details,
                    )
                    Call.sendMessage("[scarlet]Player " + player.plainName() + " has been banned")
                }
            }
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
