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

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.account.Account
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.AchievementCompletedMessage
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.sessionKey
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Iconc
import mindustry.gen.Player

class AccountListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val accounts = instances.get<AccountManager>()
    private val users = instances.get<UserManager>()
    private val playtime = ConcurrentHashMap<Player, Long>()
    private val messenger = instances.get<Messenger>()

    override fun onImperiumInit() {
        messenger.consumer<AchievementCompletedMessage> { message ->
            for (player in Entities.getPlayersAsync()) {
                val account = accounts.selectBySession(player.sessionKey)
                if (account != null && account.id == message.account) {
                    Call.warningToast(
                        player.con,
                        Iconc.infoCircle.code,
                        "Congrats, you obtained the achievement [orange]${message.achievement.name.lowercase()}.",
                    )
                    break
                }
            }
        }
    }

    @TaskHandler(delay = 1L, interval = 1L, unit = MindustryTimeUnit.MINUTES)
    internal fun onPlaytimeAchievementCheck() =
        ImperiumScope.MAIN.launch {
            for (player in Entities.getPlayersAsync()) {
                val account = accounts.selectBySession(player.sessionKey) ?: continue
                val now = System.currentTimeMillis()
                val playtime = (now - (playtime[player] ?: now)).milliseconds
                checkPlaytimeAchievements(account, playtime)
            }
        }

    @EventHandler
    internal fun onPlayerJoin(event: EventType.PlayerJoin) {
        playtime[event.player] = System.currentTimeMillis()
        ImperiumScope.MAIN.launch { users.incrementJoins(event.player.identity) }
    }

    @EventHandler
    internal fun onGameOver(event: EventType.GameOverEvent) {
        Entities.getPlayers().forEach { player ->
            ImperiumScope.MAIN.launch {
                val account = accounts.selectBySession(player.sessionKey) ?: return@launch
                accounts.incrementGames(account.id)
            }
        }
    }

    @EventHandler
    internal fun onPlayerLeave(event: EventType.PlayerLeave) {
        val playerPlaytime = playtime.remove(event.player)
        ImperiumScope.MAIN.launch {
            val now = System.currentTimeMillis()
            val account = accounts.selectBySession(event.player.sessionKey)
            if (account != null) {
                val playtime = (now - (playerPlaytime ?: now)).milliseconds
                accounts.incrementPlaytime(account.id, playtime)
                checkPlaytimeAchievements(account, playtime)
            }
            if (!users.getSetting(event.player.uuid(), User.Setting.REMEMBER_LOGIN)) {
                accounts.logout(event.player.sessionKey)
            }
        }
    }

    private suspend fun checkPlaytimeAchievements(account: Account, playtime: Duration) {
        if (playtime >= 8.hours) {
            accounts.updateAchievement(account.id, Achievement.GAMER, true)
        }
        checkDailyLoginAchievement(account, playtime, Achievement.ACTIVE)
        checkDailyLoginAchievement(account, playtime, Achievement.HYPER)
        val total = playtime + account.playtime
        if (total >= 1.days) {
            accounts.updateAchievement(account.id, Achievement.DAY, true)
        }
        if (total >= 7.days) {
            accounts.updateAchievement(account.id, Achievement.WEEK, true)
        }
        if (total >= 30.days) {
            accounts.updateAchievement(account.id, Achievement.MONTH, true)
        }
    }

    private suspend fun checkDailyLoginAchievement(account: Account, playtime: Duration, achievement: Achievement) {
        if (playtime < 30.minutes || accounts.selectAchievement(account.id, achievement)) return
        val now = System.currentTimeMillis()
        var last = accounts.selectMetadata(account.id, PLAYTIME_ACHIEVEMENT_LAST_GRANT)?.toLongOrNull()
        var increment = accounts.selectMetadata(account.id, PLAYTIME_ACHIEVEMENT_INCREMENT)?.toIntOrNull() ?: 0

        if (last == null) {
            last = now
            increment++
        } else {
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
        }

        val goal =
            when (achievement) {
                Achievement.ACTIVE -> 7
                Achievement.HYPER -> 30
                else -> error("Invalid achievement")
            }

        if (increment >= goal) {
            accounts.updateAchievement(account.id, achievement, true)
        } else {
            accounts.updateMetadata(account.id, PLAYTIME_ACHIEVEMENT_LAST_GRANT, last.toString())
            accounts.updateMetadata(account.id, PLAYTIME_ACHIEVEMENT_INCREMENT, increment.toString())
        }
    }

    companion object {
        private const val PLAYTIME_ACHIEVEMENT_LAST_GRANT = "playtime_achievement_last_grant"
        private const val PLAYTIME_ACHIEVEMENT_INCREMENT = "playtime_achievement_increment"
    }
}
