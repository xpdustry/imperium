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
package com.xpdustry.imperium.mindustry.account

import arc.Core
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.account.Account
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.AchievementCompletedMessage
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.tryGrantAdmin
import com.xpdustry.imperium.mindustry.security.GatekeeperPipeline
import com.xpdustry.imperium.mindustry.security.GatekeeperResult
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
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

    override fun onImperiumInit() {
        Core.settings.remove("imperium-granted-session-achievements")
        Core.settings.remove("imperium-granted-session-achievements-v2")

        // Small hack to make sure a player session is refreshed when it joins the server,
        // instead of blocking the process in a PlayerConnectionConfirmed event listener
        pipeline.register("account", Priority.LOWEST) {
            val identity = Identity.Mindustry(it.name, it.uuid, it.usid, it.address)
            accounts.refresh(identity)
            GatekeeperResult.Success
        }

        messenger.consumer<AchievementCompletedMessage> { message ->
            for (player in Entities.getPlayersAsync()) {
                val account = accounts.findByIdentity(player.identity)
                if (account != null && account.id == message.account) {
                    Call.warningToast(
                        player.con,
                        Iconc.infoCircle.code,
                        "Congrats, you obtained the achievement [orange]${message.achievement.name.lowercase()}.")
                    break
                }
            }
        }
    }

    @TaskHandler(delay = 1L, interval = 1L, unit = MindustryTimeUnit.MINUTES)
    internal fun onPlaytimeAchievementCheck() =
        ImperiumScope.MAIN.launch {
            for (player in Entities.getPlayersAsync()) {
                val account = accounts.findByIdentity(player.identity) ?: continue
                val now = System.currentTimeMillis()
                val playtime = (now - (playtime[player] ?: now)).milliseconds
                checkPlaytimeAchievements(account, playtime)
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
                accounts.incrementGames(account.id)
            }
        }
    }

    @EventHandler
    internal fun onPlayerLeave(event: EventType.PlayerLeave) {
        val playerPlaytime = playtime.remove(event.player)
        ImperiumScope.MAIN.launch {
            val now = System.currentTimeMillis()
            val account = accounts.findByIdentity(event.player.identity)
            if (account != null) {
                val playtime = (now - (playerPlaytime ?: now)).milliseconds
                accounts.incrementPlaytime(account.id, playtime)
                checkPlaytimeAchievements(account, playtime)
            }
            if (!users.getSetting(event.player.uuid(), User.Setting.REMEMBER_LOGIN)) {
                accounts.logout(event.player.identity)
            }
        }
    }

    private suspend fun checkPlaytimeAchievements(account: Account, playtime: Duration) {
        if (playtime >= 8.hours) {
            accounts.setAchievementCompletion(account.id, Account.Achievement.GAMER, true)
        }
        checkDailyLoginAchievement(account, playtime, Account.Achievement.ACTIVE)
        checkDailyLoginAchievement(account, playtime, Account.Achievement.HYPER)
        val total = playtime + account.playtime
        if (total >= 1.days) {
            accounts.setAchievementCompletion(account.id, Account.Achievement.DAY, true)
        }
        if (total >= 7.days) {
            accounts.setAchievementCompletion(account.id, Account.Achievement.WEEK, true)
        }
        if (total >= 30.days) {
            accounts.setAchievementCompletion(account.id, Account.Achievement.MONTH, true)
        }
    }

    private suspend fun checkDailyLoginAchievement(
        account: Account,
        playtime: Duration,
        achievement: Account.Achievement,
    ) {
        require(
            achievement == Account.Achievement.ACTIVE || achievement == Account.Achievement.HYPER)
        val progression = accounts.getAchievement(account.id, achievement)
        if (playtime < 30.minutes || progression.completed) return
        val now = System.currentTimeMillis()
        var last = progression.data["last_grant"]?.jsonPrimitive?.longOrNull ?: now
        var increment = progression.data["increment"]?.jsonPrimitive?.intOrNull ?: 0
        val elapsed = (now - last).coerceAtLeast(0).milliseconds
        if (elapsed < 1.days) {
            return
        } else if (elapsed < (2.days + 12.hours)) {
            last = now
            increment++
        } else {
            last = now
            increment = 1
        }
        val goal =
            when (achievement) {
                Account.Achievement.ACTIVE -> 7
                Account.Achievement.HYPER -> 30
                else -> error("Invalid achievement")
            }
        if (increment >= goal) {
            accounts.setAchievementCompletion(account.id, achievement, true)
        } else {
            accounts.setAchievementProgression(
                account.id,
                achievement,
                buildJsonObject {
                    put("last_grant", last)
                    put("increment", increment)
                })
        }
    }
}
