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
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.buildCache
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration
import kotlinx.coroutines.launch
import mindustry.game.EventType

class AntiEvadeListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val quits =
        buildCache<MindustryUUID, String> {
            maximumSize(1000)
            expireAfterWrite(3.hours.toJavaDuration())
        }

    @EventHandler(priority = Priority.LOW)
    internal fun onPlayerJoinEvent(event: EventType.PlayerJoin) {
        val previous = quits.get(event.player.uuid()) { event.player.info.plainLastName() }
        if (event.player.info.plainLastName() != previous) {
            for (player in Entities.getPlayers()) {
                if (player.uuid() == event.player.uuid()) continue
                ImperiumScope.MAIN.launch {
                    if (users.getSetting(player.uuid(), User.Setting.ANTI_BAN_EVADE)) {
                        player.sendMessage(
                            "[orange]Warning, the player [accent]${event.player.info.plainLastName()}[] has changed his name. He was [accent]$previous[]."
                        )
                    }
                }
            }
        }
    }

    @EventHandler(priority = Priority.LOW)
    internal fun onPlayerQuitEvent(event: EventType.PlayerLeave) {
        quits.put(event.player.uuid(), event.player.info.plainLastName())
    }
}
