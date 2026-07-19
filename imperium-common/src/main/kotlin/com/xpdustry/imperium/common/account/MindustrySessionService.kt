// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.string.ImperiumArgon2
import com.xpdustry.imperium.common.string.Password
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

@Inject
class MindustrySessionService(
    private val provider: SQLProvider,
    private val accounts: AccountService,
    private val config: ImperiumConfig,
) : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction {
            SchemaUtils.createMissingTablesAndColumns(AccountTable, AccountSessionTable)
            AccountSessionTable.deleteWhere { expiration less Clock.System.now() }
        }
    }

    suspend fun selectByKey(key: SessionKey): MindustrySession? =
        provider.newSuspendTransaction {
            AccountSessionTable.selectAll()
                .where {
                    (AccountSessionTable.uuid eq key.uuid) and
                        (AccountSessionTable.usid eq key.usid) and
                        (AccountSessionTable.address eq key.address.address) and
                        (AccountSessionTable.expiration greaterEq Clock.System.now())
                }
                .firstOrNull()
                ?.toMindustrySession(key)
        }

    suspend fun selectAllByAccount(account: Int): List<MindustrySession> =
        provider.newSuspendTransaction {
            AccountSessionTable.selectAll()
                .where {
                    (AccountSessionTable.account eq account) and
                        (AccountSessionTable.expiration greaterEq Clock.System.now())
                }
                .map {
                    val address = java.net.InetAddress.getByAddress(it[AccountSessionTable.address])
                    it.toMindustrySession(
                        SessionKey(it[AccountSessionTable.uuid], it[AccountSessionTable.usid], address)
                    )
                }
        }

    suspend fun login(key: SessionKey, username: String, password: Password): AccountResult {
        if (selectByKey(key) != null) {
            return AccountResult.AlreadyLogged
        }

        val targetAccount = accounts.selectByUsername(username) ?: return AccountResult.NotFound
        val current = accounts.selectPasswordById(targetAccount.id) ?: return AccountResult.NotFound
        if (!ImperiumArgon2.equals(password, current.hash, current.salt)) {
            return AccountResult.NotFound
        }

        provider.newSuspendTransaction {
            AccountSessionTable.upsert {
                val now = Clock.System.now()
                it[AccountSessionTable.account] = targetAccount.id
                it[uuid] = key.uuid
                it[usid] = key.usid
                it[address] = key.address.address
                it[server] = config.server.name
                it[creation] = now
                it[lastLogin] = now
                it[expiration] = now.plus(SESSION_LIFETIME)
            }
        }

        return AccountResult.Success
    }

    suspend fun logout(key: SessionKey, all: Boolean = false): Boolean =
        provider.newSuspendTransaction {
            if (!all) {
                AccountSessionTable.deleteWhere {
                    (uuid eq key.uuid) and (usid eq key.usid) and (address eq key.address.address)
                } > 0
            } else {
                val accounts =
                    AccountSessionTable.select(AccountSessionTable.account)
                        .where { AccountSessionTable.uuid eq key.uuid }
                        .map { it[AccountSessionTable.account].value }
                if (accounts.isEmpty()) {
                    false
                } else {
                    AccountSessionTable.deleteWhere { account inList accounts } > 0
                }
            }
        }

    private fun ResultRow.toMindustrySession(key: SessionKey) =
        MindustrySession(
            key = key,
            account = this[AccountSessionTable.account].value,
            creation = this[AccountSessionTable.creation],
            expiration = this[AccountSessionTable.expiration],
            lastLogin = this[AccountSessionTable.lastLogin],
        )

    companion object {
        private val SESSION_LIFETIME = 30.days
    }
}
