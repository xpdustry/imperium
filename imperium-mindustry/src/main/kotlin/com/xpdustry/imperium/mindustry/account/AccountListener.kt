// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.distributor.api.translation.TranslationArguments
import com.xpdustry.imperium.common.account.Account
import com.xpdustry.imperium.common.account.AccountAchievementService
import com.xpdustry.imperium.common.account.AccountMetadataService
import com.xpdustry.imperium.common.account.AccountService
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.AchievementCompletedMessage
import com.xpdustry.imperium.common.account.MindustrySessionService
import com.xpdustry.imperium.common.account.selectAccount
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.store.DataStoreService
import com.xpdustry.imperium.mindustry.translation.cyan_prefix
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

@Inject
class AccountListener(
    private val accounts: AccountService,
    private val achievements: AccountAchievementService,
    private val metadata: AccountMetadataService,
    private val sessions: MindustrySessionService,
    private val store: DataStoreService,
    private val users: UserManager,
    private val messenger: MessageService,
) : ImperiumApplication.Listener {
    private val playtime = ConcurrentHashMap<Player, Long>()

    override fun onImperiumInit() {
        messenger.subscribe<AchievementCompletedMessage> { message ->
            for (player in Entities.getPlayersAsync()) {
                if (store.selectAccountBySessionKey(player.sessionKey)?.account?.id == message.account) {
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
                val account = store.selectAccountBySessionKey(player.sessionKey)?.account ?: continue
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
            val account = sessions.selectAccount(accounts, event.player.sessionKey) ?: return@launch
            event.player.asAudience.sendMessage(
                cyan_prefix(
                    translatable()
                        .setKey("imperium.notification.login")
                        .setParameters(TranslationArguments.array(text(account.username, ComponentColor.ACCENT)))
                        .build()
                )
            )
        }
    }

    @EventHandler
    internal fun onGameOver(event: EventType.GameOverEvent) {
        Entities.getPlayers().forEach { player ->
            ImperiumScope.MAIN.launch {
                val account = sessions.selectAccount(accounts, player.sessionKey) ?: return@launch
                accounts.incrementGames(account.id)
            }
        }
    }

    @EventHandler
    internal fun onPlayerLeave(event: EventType.PlayerLeave) {
        val playerPlaytime = playtime.remove(event.player)
        ImperiumScope.MAIN.launch {
            val now = System.currentTimeMillis()
            val account = sessions.selectAccount(accounts, event.player.sessionKey)
            if (account != null) {
                val playtime = (now - (playerPlaytime ?: now)).milliseconds
                accounts.incrementPlaytime(account.id, playtime)
                checkPlaytimeAchievements(account, playtime)
            }
            if (!users.getSetting(event.player.uuid(), User.Setting.REMEMBER_LOGIN)) {
                sessions.logout(event.player.sessionKey)
            }
        }
    }

    private suspend fun checkPlaytimeAchievements(account: Account, playtime: Duration) {
        if (playtime >= 8.hours) {
            achievements.updateAchievement(account.id, Achievement.GAMER, true)
        }
        checkDailyLoginAchievement(account, playtime, Achievement.ACTIVE)
        checkDailyLoginAchievement(account, playtime, Achievement.HYPER)
        val total = playtime + account.playtime
        if (total >= 1.days) {
            achievements.updateAchievement(account.id, Achievement.DAY, true)
        }
        if (total >= 7.days) {
            achievements.updateAchievement(account.id, Achievement.WEEK, true)
        }
        if (total >= 30.days) {
            achievements.updateAchievement(account.id, Achievement.MONTH, true)
        }
    }

    private suspend fun checkDailyLoginAchievement(account: Account, playtime: Duration, achievement: Achievement) {
        if (playtime < 30.minutes || achievements.selectAchievement(account.id, achievement)) return
        val now = System.currentTimeMillis()
        var last = metadata.selectMetadata(account.id, PLAYTIME_ACHIEVEMENT_LAST_GRANT)?.toLongOrNull()
        var increment = metadata.selectMetadata(account.id, PLAYTIME_ACHIEVEMENT_INCREMENT)?.toIntOrNull() ?: 0

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
            achievements.updateAchievement(account.id, achievement, true)
        } else {
            metadata.updateMetadata(account.id, PLAYTIME_ACHIEVEMENT_LAST_GRANT, last.toString())
            metadata.updateMetadata(account.id, PLAYTIME_ACHIEVEMENT_INCREMENT, increment.toString())
        }
    }

    companion object {
        private const val PLAYTIME_ACHIEVEMENT_LAST_GRANT = "playtime_achievement_last_grant"
        private const val PLAYTIME_ACHIEVEMENT_INCREMENT = "playtime_achievement_increment"
    }
}
