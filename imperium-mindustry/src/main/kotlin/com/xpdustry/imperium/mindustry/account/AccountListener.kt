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
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.tryGrantAdmin
import com.xpdustry.imperium.mindustry.security.GatekeeperPipeline
import com.xpdustry.imperium.mindustry.security.GatekeeperResult
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.util.Priority
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
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
            users.incrementJoins(event.player.identity)
            event.player.tryGrantAdmin(accounts)
        }
    }

    @EventHandler
    internal fun onGameOver(event: EventType.GameOverEvent) {
        Entities.getPlayers().forEach { player ->
            ImperiumScope.MAIN.launch {
                val account = accounts.findByIdentity(player.identity) ?: return@launch
                accounts.incrementGames(account.snowflake)
            }
        }
    }

    @EventHandler
    internal fun onPlayerLeave(event: EventType.PlayerLeave) =
        ImperiumScope.MAIN.launch {
            val now = System.currentTimeMillis()

            val account = accounts.findByIdentity(event.player.identity)
            if (account != null) {
                accounts.incrementPlaytime(
                    account.snowflake, (now - (playtime.remove(event.player) ?: now)).milliseconds)
            }
            if (users.getSetting(event.player.uuid(), User.Setting.REMEMBER_LOGIN)) {
                accounts.logout(event.player.identity)
            }
        }
}
