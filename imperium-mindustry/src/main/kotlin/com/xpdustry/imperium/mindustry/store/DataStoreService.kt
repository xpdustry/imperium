// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.store

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.account.AccountAchievementService
import com.xpdustry.imperium.common.account.AccountMetadataService
import com.xpdustry.imperium.common.account.AccountService
import com.xpdustry.imperium.common.account.AccountUpdate
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.AchievementUpdate
import com.xpdustry.imperium.common.account.MetadataUpdate
import com.xpdustry.imperium.common.account.MindustrySessionService
import com.xpdustry.imperium.common.account.SessionKey
import com.xpdustry.imperium.common.account.selectAccount
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.mindustry.account.PlayerLoginEvent
import com.xpdustry.imperium.mindustry.account.PlayerLogoutEvent
import com.xpdustry.imperium.mindustry.misc.sessionKey
import java.util.EnumSet.noneOf
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import mindustry.game.EventType

@Inject
class DataStoreService(
    private val accounts: AccountService,
    private val achievements: AccountAchievementService,
    private val metadata: AccountMetadataService,
    private val sessions: MindustrySessionService,
    private val messenger: MessageService,
) : ImperiumApplication.Listener {
    private val data = ConcurrentHashMap<SessionKey, StoredAccount>()

    override fun onImperiumInit() {
        messenger.subscribe<AccountUpdate> { message ->
            val account = accounts.selectById(message.account)
            refresh(message.account) { current ->
                if (account == null) {
                    null
                } else {
                    current.copy(account = account)
                }
            }
        }

        messenger.subscribe<AchievementUpdate> { message ->
            refresh(message.account) {
                it.copy(
                    achievements =
                        noneOf(Achievement::class.java).apply {
                            addAll(it.achievements)
                            if (message.completed) add(message.achievement) else remove(message.achievement)
                        }
                )
            }
        }

        messenger.subscribe<MetadataUpdate> { message ->
            refresh(message.account) {
                it.copy(
                    metadata =
                        it.metadata.toMutableMap().apply {
                            if (message.value == null) remove(message.key) else this[message.key] = message.value!!
                        }
                )
            }
        }
    }

    fun selectAccountBySessionKey(key: SessionKey): StoredAccount? = data[key]

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        load(event.player.sessionKey)
    }

    @EventHandler
    fun onPlayerLogin(event: PlayerLoginEvent) {
        load(event.player.sessionKey)
    }

    @EventHandler
    fun onPlayerLogout(event: PlayerLogoutEvent) {
        data.remove(event.player.sessionKey)
    }

    @EventHandler
    fun onPlayerLeave(event: EventType.PlayerLeave) {
        data.remove(event.player.sessionKey)
    }

    private fun load(key: SessionKey) {
        ImperiumScope.MAIN.launch {
            val account =
                sessions.selectAccount(accounts, key)
                    ?: run {
                        data.remove(key)
                        return@launch
                    }

            data[key] =
                StoredAccount(
                    account = account,
                    achievements = achievements.selectAchievements(account.id),
                    metadata = metadata.selectAllMetadata(account.id),
                )
        }
    }

    private fun refresh(account: Int, update: (StoredAccount) -> StoredAccount?) {
        for ((key, value) in data.entries) {
            if (value.account.id != account) {
                continue
            }

            val next = update(value)
            if (next == null) {
                data.remove(key)
            } else {
                data[key] = next
            }
        }
    }
}
