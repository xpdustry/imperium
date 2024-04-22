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

import com.xpdustry.distributor.annotation.method.EventHandler
import com.xpdustry.distributor.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.buildCache
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.PunishmentMessage
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Player

interface FreezeManager {

    fun setTemporaryFreeze(player: Player, freeze: Freeze?)

    fun getFreeze(player: Player): Freeze?

    data class Freeze(val reason: String, val punishment: Snowflake? = null)
}

internal class SimpleFreezeManager(
    plugin: MindustryPlugin,
    private val punishments: PunishmentManager,
    private val users: UserManager,
    private val messenger: Messenger,
) : FreezeManager, ImperiumApplication.Listener {

    private val temporary = PlayerMap<FreezeManager.Freeze>(plugin)
    private val cache =
        buildCache<MindustryUUID, Punishment> {
            maximumSize(500) // FailSafe in case of a memory leak
        }

    override fun onImperiumInit() {
        messenger.consumer<PunishmentMessage> { message ->
            val punishment = punishments.findBySnowflake(message.snowflake) ?: return@consumer
            val target = users.findBySnowflake(punishment.target) ?: return@consumer
            if (punishment.type == Punishment.Type.FREEZE) {
                Entities.getPlayersAsync()
                    .filter {
                        it.uuid() == target.uuid || it.ip().toInetAddress() == target.lastAddress
                    }
                    .forEach { refreshFreezeData(it) }
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        ImperiumScope.MAIN.launch { refreshFreezeData(event.player) }
    }

    @EventHandler
    fun onPlayerQuit(event: EventType.PlayerLeave) {
        cache.invalidate(event.player.uuid())
    }

    private suspend fun refreshFreezeData(player: Player) {
        cache.invalidate(player.uuid())
        val freeze =
            punishments
                .findAllByIdentity(player.identity)
                .filter { it.type == Punishment.Type.FREEZE && !it.expired }
                .maxByOrNull { it.duration } ?: return
        runMindustryThread {
            if (player.con.isConnected && !player.con.kicked) {
                cache.put(player.uuid(), freeze)
            }
        }
    }

    override fun setTemporaryFreeze(player: Player, freeze: FreezeManager.Freeze?) {
        if (freeze == null) temporary.remove(player) else temporary[player] = freeze
    }

    override fun getFreeze(player: Player): FreezeManager.Freeze? {
        val punishment = cache.getIfPresent(player.uuid()) ?: return temporary[player]

        if (punishment.expired) {
            cache.invalidate(player.uuid())
        } else {
            return FreezeManager.Freeze(punishment.reason, punishment.snowflake)
        }

        return temporary[player]
    }
}
