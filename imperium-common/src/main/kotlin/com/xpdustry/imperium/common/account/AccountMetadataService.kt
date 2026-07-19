// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.message.MessageService
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

@Inject
class AccountMetadataService(private val provider: SQLProvider, private val messenger: MessageService) :
    ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.createMissingTablesAndColumns(AccountTable, AccountMetadataTable) }
    }

    suspend fun selectMetadata(account: Int, key: String): String? {
        validateKey(key)
        return provider.newSuspendTransaction {
            AccountMetadataTable.select(AccountMetadataTable.value)
                .where { (AccountMetadataTable.account eq account) and (AccountMetadataTable.key eq key) }
                .firstOrNull()
                ?.get(AccountMetadataTable.value)
        }
    }

    suspend fun selectAllMetadata(account: Int): Map<String, String> = selectAllMetadataByPrefix(account, "")

    suspend fun selectAllMetadataByPrefix(account: Int, prefix: String): Map<String, String> {
        validateKey(prefix)
        return provider.newSuspendTransaction {
            AccountMetadataTable.select(AccountMetadataTable.key, AccountMetadataTable.value)
                .where { (AccountMetadataTable.account eq account) and (AccountMetadataTable.key like "$prefix%") }
                .associate { it[AccountMetadataTable.key] to it[AccountMetadataTable.value] }
        }
    }

    suspend fun updateMetadata(account: Int, key: String, value: String): Boolean {
        validateKey(key)
        val updated =
            provider.newSuspendTransaction {
                AccountMetadataTable.upsert {
                    it[AccountMetadataTable.account] = account
                    it[AccountMetadataTable.key] = key
                    it[AccountMetadataTable.value] = value
                }
                true
            }
        if (updated) {
            messenger.broadcast(MetadataUpdate(account, key, value))
        }
        return updated
    }

    suspend fun deleteMetadata(account: Int, key: String): Boolean {
        validateKey(key)
        val deleted =
            provider.newSuspendTransaction {
                AccountMetadataTable.deleteWhere {
                    (AccountMetadataTable.account eq account) and (AccountMetadataTable.key eq key)
                } > 0
            }
        if (deleted) {
            messenger.broadcast(MetadataUpdate(account, key, null))
        }
        return deleted
    }

    private fun validateKey(key: String) {
        require(KEY_REGEX.matches(key)) { "Invalid key: $key" }
    }

    companion object {
        private val KEY_REGEX = Regex("^[a-z0-9_]*$")
    }
}
