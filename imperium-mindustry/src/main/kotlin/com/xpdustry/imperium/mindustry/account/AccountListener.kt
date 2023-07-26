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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.Account
import com.xpdustry.imperium.common.database.Database
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.service.AccountService
import com.xpdustry.imperium.common.service.PlayerIdentity
import com.xpdustry.imperium.mindustry.verification.VerificationPipeline
import com.xpdustry.imperium.mindustry.verification.VerificationResult
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.util.Priority
import mindustry.game.EventType
import mindustry.gen.Groups
import mindustry.gen.Player
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class AccountListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val service: AccountService = instances.get()
    private val pipeline: VerificationPipeline = instances.get()
    private val database: Database = instances.get()

    private val playtime = ConcurrentHashMap<Player, Long>()

    override fun onImperiumInit() {
        // Small hack to make sure a player session is refreshed when it joins the server,
        // instead of blocking the process in a PlayerConnectionConfirmed event listener
        pipeline.register("account", Priority.LOWEST) {
            service.refresh(PlayerIdentity(it.uuid, it.usid, it.address)).thenReturn(VerificationResult.Success)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        playtime[event.player] = System.currentTimeMillis()
        database.users.updateOrCreate(event.player.uuid()) { user ->
            val address = event.player.ip().toInetAddress()
            val name = event.player.plainName()
            user.timesJoined += 1
            user.lastName = name
            user.names += name
            user.lastAddress = address
            user.addresses += address
        }.subscribe()
    }

    @EventHandler
    fun onGameOver(event: EventType.GameOverEvent) {
        Groups.player.forEach { player ->
            findAccountByIdentityAndUpdate(player.identity) { account ->
                account.games++
            }
        }
    }

    @EventHandler
    fun onPlayerLeave(event: EventType.PlayerLeave) {
        val now = System.currentTimeMillis()
        findAccountByIdentityAndUpdate(event.player.identity) { account ->
            account.playtime += Duration.ofMillis(now - (playtime.remove(event.player) ?: now))
        }
    }

    private fun findAccountByIdentityAndUpdate(identity: PlayerIdentity, update: (Account) -> Unit) {
        service.findAccountByIdentity(identity)
            .flatMap { account ->
                update(account)
                database.accounts.save(account)
            }
            .subscribe()
    }
}

val Player.identity: PlayerIdentity get() = PlayerIdentity(uuid(), usid(), con.address.toInetAddress())
