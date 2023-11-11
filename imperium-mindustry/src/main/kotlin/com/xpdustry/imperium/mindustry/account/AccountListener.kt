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
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Role
import com.xpdustry.imperium.common.account.User
import com.xpdustry.imperium.common.account.UserManager
import com.xpdustry.imperium.common.account.containsRole
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.security.GatekeeperPipeline
import com.xpdustry.imperium.mindustry.security.GatekeeperResult
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.util.Priority
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Player

class AccountListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val pipeline = instances.get<GatekeeperPipeline>()
    private val accounts = instances.get<AccountManager>()
    private val users = instances.get<UserManager>()
    private val playtime = ConcurrentHashMap<Player, Long>()

    override fun onImperiumInit() {
        // Small hack to make sure a player session is refreshed when it joins the server,
        // instead of blocking the process in a PlayerConnectionConfirmed event listener
        pipeline.register("account", Priority.LOWEST) {
            accounts.refresh(Identity.Mindustry(it.name, it.uuid, it.usid, it.address))
            GatekeeperResult.Success
        }
    }

    @EventHandler
    internal fun onPlayerJoin(event: EventType.PlayerJoin) {
        playtime[event.player] = System.currentTimeMillis()
        ImperiumScope.MAIN.launch {
            users.updateOrCreateByUuid(event.player.uuid()) { user ->
                user.timesJoined += 1
                user.lastName = event.player.plainName()
                user.names += event.player.plainName()
                user.lastAddress = event.player.ip().toInetAddress()
                user.addresses += event.player.ip().toInetAddress()
                user.lastJoin = Instant.now()
            }

            // Grants admin to moderators
            event.player.admin =
                accounts.findByIdentity(event.player.identity)?.roles?.containsRole(Role.MODERATOR)
                    ?: false
        }
    }

    @EventHandler
    internal fun onGameOver(event: EventType.GameOverEvent) {
        Entities.PLAYERS.forEach { player ->
            ImperiumScope.MAIN.launch {
                accounts.updateByIdentity(player.identity) { account -> account.games++ }
            }
        }
    }

    @EventHandler
    internal fun onPlayerLeave(event: EventType.PlayerLeave) =
        ImperiumScope.MAIN.launch {
            val now = System.currentTimeMillis()
            accounts.updateByIdentity(event.player.identity) { account ->
                account.playtime += Duration.ofMillis(now - (playtime.remove(event.player) ?: now))
            }
            if (!users.getSetting(event.player.uuid(), User.Setting.REMEMBER_LOGIN)) {
                accounts.logout(event.player.identity)
            }
        }
}
