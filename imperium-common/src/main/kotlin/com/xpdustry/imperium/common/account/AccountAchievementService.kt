// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.misc.exists
import java.util.EnumSet
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

@Inject
class AccountAchievementService(
    private val provider: SQLProvider,
    private val messenger: MessageService,
    private val accounts: AccountService,
) : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.createMissingTablesAndColumns(AccountTable, AccountAchievementTable) }
    }

    suspend fun selectAchievement(account: Int, achievement: Achievement): Boolean =
        provider.newSuspendTransaction {
            AccountAchievementTable.exists {
                (AccountAchievementTable.account eq account) and (AccountAchievementTable.achievement eq achievement)
            }
        }

    suspend fun selectAchievements(account: Int): Set<Achievement> =
        provider.newSuspendTransaction {
            AccountAchievementTable.select(AccountAchievementTable.achievement)
                .where { AccountAchievementTable.account eq account }
                .mapTo(EnumSet.noneOf(Achievement::class.java)) { it[AccountAchievementTable.achievement] }
        }

    suspend fun updateAchievement(account: Int, achievement: Achievement, completed: Boolean): Boolean {
        if (!accounts.existsById(account)) {
            return false
        }

        val updated =
            provider.newSuspendTransaction {
                if (completed) {
                    AccountAchievementTable.upsert {
                        it[AccountAchievementTable.account] = account
                        it[AccountAchievementTable.achievement] = achievement
                        it[AccountAchievementTable.completed] = true
                    }
                    true
                } else {
                    AccountAchievementTable.deleteWhere {
                        (AccountAchievementTable.account eq account) and
                            (AccountAchievementTable.achievement eq achievement)
                    } > 0
                }
            }

        if (!updated) {
            return false
        }

        messenger.broadcast(AchievementUpdate(account, achievement, completed))
        if (completed) {
            messenger.broadcast(AchievementCompletedMessage(account, achievement))
        }
        return true
    }
}
