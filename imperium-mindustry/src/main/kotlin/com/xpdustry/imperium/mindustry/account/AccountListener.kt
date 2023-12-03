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

import arc.Core
import com.xpdustry.imperium.common.account.Account
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.AchievementCompletedMessage
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.tryGrantAdmin
import com.xpdustry.imperium.mindustry.security.GatekeeperPipeline
import com.xpdustry.imperium.mindustry.security.GatekeeperResult
import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.event.EventHandler
import fr.xpdustry.distributor.api.util.MUUID
import fr.xpdustry.distributor.api.util.Priority
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Iconc
import mindustry.gen.Player

class AccountListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val pipeline = instances.get<GatekeeperPipeline>()
    private val accounts = instances.get<AccountManager>()
    private val users = instances.get<UserManager>()
    private val playtime = ConcurrentHashMap<Player, Long>()
    private val messenger = instances.get<Messenger>()
    private val grantedSessionAchievements = ConcurrentHashMap<Snowflake, Long>()

    override fun onImperiumInit() {
        grantedSessionAchievements +=
            Json.decodeFromString<Map<Snowflake, Long>>(
                Core.settings.getString("imperium-granted-session-achievements", "{}"))

        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(1.minutes)
                grantedSessionAchievements.values.removeAll {
                    (System.currentTimeMillis() - it).milliseconds >= 1.days
                }
                for (player in Entities.getPlayersAsync()) {
                    val account = accounts.findByIdentity(player.identity) ?: continue
                    val now = System.currentTimeMillis()
                    val playtime = (now - (playtime[player] ?: now)).milliseconds
                    checkPlaytimeAchievements(account, playtime)
                }
                runMindustryThread {
                    Core.settings.put(
                        "imperium-granted-session-achievements",
                        Json.encodeToString<Map<Snowflake, Long>>(grantedSessionAchievements))
                }
            }
        }

        // Small hack to make sure a player session is refreshed when it joins the server,
        // instead of blocking the process in a PlayerConnectionConfirmed event listener
        pipeline.register("account", Priority.LOWEST) {
            val identity = Identity.Mindustry(it.name, it.uuid, it.usid, it.address)
            accounts.refresh(identity)
            if ((accounts.findByIdentity(identity)?.rank ?: Rank.EVERYONE) >= Rank.VERIFIED) {
                DistributorProvider.get()
                    .playerValidator
                    .validate(MUUID.of(identity.uuid, identity.usid))
            }
            GatekeeperResult.Success
        }

        messenger.consumer<AchievementCompletedMessage> { message ->
            for (player in Entities.getPlayersAsync()) {
                val account = accounts.findByIdentity(player.identity)
                if (account != null && account.snowflake == message.account) {
                    Call.warningToast(
                        player.con,
                        Iconc.infoCircle.code,
                        "Congrats, you obtained the achievement [orange]${message.achievement.name.lowercase()}.")
                    break
                }
            }
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
                val playtime = (now - (playtime.remove(event.player) ?: now)).milliseconds
                accounts.incrementPlaytime(account.snowflake, playtime)
                checkPlaytimeAchievements(account, playtime)
            }
            if (!users.getSetting(event.player.uuid(), User.Setting.REMEMBER_LOGIN)) {
                accounts.logout(event.player.identity)
            }
        }

    private suspend fun checkPlaytimeAchievements(account: Account, playtime: Duration) {
        if (playtime >= 8.hours) {
            accounts.progress(account.snowflake, Account.Achievement.GAMER)
        }
        if (playtime >= 30.minutes && !grantedSessionAchievements.containsKey(account.snowflake)) {
            accounts.progress(account.snowflake, Account.Achievement.ACTIVE)
            accounts.progress(account.snowflake, Account.Achievement.HYPER)
            grantedSessionAchievements[account.snowflake] = System.currentTimeMillis()
        }
        val total = playtime + account.playtime
        if (total >= 1.days) {
            accounts.progress(account.snowflake, Account.Achievement.DAY)
        }
        if (total >= 7.days) {
            accounts.progress(account.snowflake, Account.Achievement.WEEK)
        }
        if (total >= 30.days) {
            accounts.progress(account.snowflake, Account.Achievement.MONTH)
        }
    }
}
